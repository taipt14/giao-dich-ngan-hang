package vn.bank.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.account.domain.AccountHolder;
import vn.bank.account.domain.AccountType;
import vn.bank.account.domain.CashDeposit;
import vn.bank.account.domain.EntryDirection;
import vn.bank.account.domain.Journal;
import vn.bank.account.domain.LedgerEntry;
import vn.bank.account.domain.PostingLine;
import vn.bank.account.domain.ReferenceType;
import vn.bank.account.infrastructure.persistence.AccountRepository;
import vn.bank.common.error.BusinessException;

class CreateDepositServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private static final String ACCOUNT_NO = "100000000001";
    private static final String IDEMPOTENCY_KEY = "6f1c2d6e-0000-4000-8000-000000000023";
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000.00");
    private static final DepositCommand COMMAND =
            new DepositCommand(ACCOUNT_NO, IDEMPOTENCY_KEY, ONE_MILLION, "Nap tien tai quay");

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final SubmitPostingService submitPostingService = mock(SubmitPostingService.class);
    private final CreateDepositService service = new CreateDepositService(accountRepository, submitPostingService);

    @Test
    void should_postDepositJournalWithIdempotencyKey_when_deposit() {
        // given
        givenAccount(customerAccount());
        var posted = postingResult(ACCOUNT_NO, ONE_MILLION, true);
        when(submitPostingService.submit(any())).thenReturn(posted);

        // when
        var result = service.deposit(COMMAND);

        // then
        var command = ArgumentCaptor.forClass(PostingCommand.class);
        verify(submitPostingService).submit(command.capture());
        assertThat(command.getValue().referenceType()).isEqualTo(ReferenceType.DEPOSIT);
        assertThat(command.getValue().referenceId()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(command.getValue().description()).isEqualTo("Nap tien tai quay");
        assertThat(command.getValue().lines())
                .containsExactly(
                        new PostingLine(CashDeposit.CASH_VAULT, EntryDirection.DEBIT, ONE_MILLION),
                        new PostingLine(ACCOUNT_NO, EntryDirection.CREDIT, ONE_MILLION));
        assertThat(result.created()).isTrue();
        assertThat(result.journal()).isSameAs(posted.journal());
        assertThat(result.creditEntry().getAccountNo()).isEqualTo(ACCOUNT_NO);
        assertThat(result.creditEntry().getBalanceAfter()).isEqualByComparingTo(ONE_MILLION);
    }

    @Test
    void should_returnPreviousDeposit_when_sameIdempotencyKeyAlreadyPosted() {
        // given
        givenAccount(customerAccount());
        var existing = postingResult(ACCOUNT_NO, ONE_MILLION, false);
        when(submitPostingService.submit(any())).thenReturn(existing);

        // when
        var result = service.deposit(COMMAND);

        // then
        assertThat(result.created()).isFalse();
        assertThat(result.journal()).isSameAs(existing.journal());
        assertThat(result.creditEntry()).isSameAs(existing.entries().get(1));
    }

    @Test
    void should_rejectIdempotencyKeyReused_when_existingDepositHasDifferentAmount() {
        // given
        givenAccount(customerAccount());
        when(submitPostingService.submit(any())).thenReturn(postingResult(ACCOUNT_NO, new BigDecimal("500.00"), false));

        // when
        var thrown = catchThrowable(() -> service.deposit(COMMAND));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void should_rejectAccountNotFound_when_accountDoesNotExist() {
        // given
        when(accountRepository.findById(ACCOUNT_NO)).thenReturn(Optional.empty());

        // when
        var thrown = catchThrowable(() -> service.deposit(COMMAND));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
        verify(submitPostingService, never()).submit(any());
    }

    @Test
    void should_notPost_when_amountExceedsLimit() {
        // given
        var command = new DepositCommand(ACCOUNT_NO, IDEMPOTENCY_KEY, new BigDecimal("500000000.01"), null);

        // when
        var thrown = catchThrowable(() -> service.deposit(command));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.DEPOSIT_LIMIT_EXCEEDED);
        verify(submitPostingService, never()).submit(any());
    }

    private void givenAccount(Account account) {
        when(accountRepository.findById(account.getAccountNo())).thenReturn(Optional.of(account));
    }

    private static PostingResult postingResult(String accountNo, BigDecimal amount, boolean created) {
        var lines = new CashDeposit(accountNo, amount).toPostingLines();
        var journal =
                Journal.create(ReferenceType.DEPOSIT, IDEMPOTENCY_KEY, "Nap tien tai quay", lines, CLOCK.instant());
        ReflectionTestUtils.setField(journal, "id", 23L);
        var debit = LedgerEntry.post(journal, glAccount(), lines.get(0));
        var credit = LedgerEntry.post(journal, customerAccount(accountNo), lines.get(1));
        return new PostingResult(journal, List.of(debit, credit), created);
    }

    private static Account customerAccount() {
        return customerAccount(ACCOUNT_NO);
    }

    private static Account customerAccount(String accountNo) {
        return Account.open(accountNo, new AccountHolder("C000000001", "ACTIVE"), AccountType.CURRENT, 0, CLOCK);
    }

    // Tai khoan GL chi tao bang migration, test dung reflection de co entity GL
    private static Account glAccount() {
        var account = customerAccount();
        ReflectionTestUtils.setField(account, "accountNo", CashDeposit.CASH_VAULT);
        ReflectionTestUtils.setField(account, "cif", null);
        ReflectionTestUtils.setField(account, "accountType", AccountType.GL);
        return account;
    }
}
