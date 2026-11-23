package vn.bank.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.account.domain.AccountHolder;
import vn.bank.account.domain.AccountType;
import vn.bank.account.infrastructure.messaging.AccountOpenedProducer;
import vn.bank.account.infrastructure.persistence.AccountRepository;
import vn.bank.common.error.BusinessException;

class CreateAccountServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC);
    private static final String CIF = "C000000001";
    private static final AccountHolder HOLDER = new AccountHolder(CIF, "ACTIVE");

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountOpenedProducer accountOpenedProducer = mock(AccountOpenedProducer.class);
    private final CreateAccountService createAccountService =
            new CreateAccountService(accountRepository, accountOpenedProducer, CLOCK);

    // Gia lap transaction dang mo de service dang ky duoc callback afterCommit
    @BeforeEach
    void startTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSynchronization() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void should_saveAccountWithNumberFromSequence_when_customerHasFewerThanThreeAccounts() {
        // given
        givenExistingAccounts(2);

        // when
        var account = createAccountService.create(HOLDER, AccountType.CURRENT);

        // then
        assertThat(account.getAccountNo()).isEqualTo("100000000042");
        assertThat(account.getCif()).isEqualTo(CIF);
        verify(accountRepository).save(account);
    }

    @Test
    void should_publishAccountOpenedOnlyAfterCommit_when_accountOpened() {
        // given
        givenExistingAccounts(0);

        // when
        var account = createAccountService.create(HOLDER, AccountType.CURRENT);

        // then
        verify(accountOpenedProducer, never()).send(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(accountOpenedProducer).send(account);
    }

    @Test
    void should_notPublishEvent_when_transactionRolledBack() {
        // given
        givenExistingAccounts(0);

        // when
        createAccountService.create(HOLDER, AccountType.CURRENT);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        // then
        verify(accountOpenedProducer, never()).send(any());
    }

    @Test
    void should_rejectFourthAccount_when_customerAlreadyHasThreeAccounts() {
        // given
        givenExistingAccounts(3);

        // when
        var thrown = catchThrowable(() -> createAccountService.create(HOLDER, AccountType.CURRENT));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_LIMIT_EXCEEDED);
        verify(accountRepository, never()).save(any());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    private void givenExistingAccounts(long count) {
        when(accountRepository.countByCifAndAccountType(CIF, AccountType.CURRENT))
                .thenReturn(count);
        when(accountRepository.nextAccountNoSequence()).thenReturn(42L);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
