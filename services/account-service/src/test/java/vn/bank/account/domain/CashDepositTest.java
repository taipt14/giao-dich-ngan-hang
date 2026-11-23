package vn.bank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import vn.bank.common.error.BusinessException;

class CashDepositTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private static final String ACCOUNT_NO = "100000000001";
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000.00");

    @Test
    void should_debitCashVaultAndCreditAccount_when_toPostingLines() {
        // given
        var deposit = new CashDeposit(ACCOUNT_NO, ONE_MILLION);

        // when
        var lines = deposit.toPostingLines();

        // then
        assertThat(lines)
                .containsExactly(
                        new PostingLine(CashDeposit.CASH_VAULT, EntryDirection.DEBIT, ONE_MILLION),
                        new PostingLine(ACCOUNT_NO, EntryDirection.CREDIT, ONE_MILLION));
    }

    @Test
    void should_acceptDeposit_when_amountEqualsMax() {
        // when
        var deposit = new CashDeposit(ACCOUNT_NO, new BigDecimal("500000000"));

        // then
        assertThat(deposit.amount()).isEqualByComparingTo(CashDeposit.MAX_AMOUNT);
    }

    @Test
    void should_rejectDeposit_when_amountExceedsMax() {
        // when
        var thrown = catchThrowable(() -> new CashDeposit(ACCOUNT_NO, new BigDecimal("500000000.01")));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.DEPOSIT_LIMIT_EXCEEDED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1"})
    void should_rejectDeposit_when_amountNotPositive(String amount) {
        // when
        var thrown = catchThrowable(() -> new CashDeposit(ACCOUNT_NO, new BigDecimal(amount)));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.INVALID_AMOUNT);
    }

    @Test
    void should_rejectDeposit_when_targetIsGlAccount() {
        // given
        var deposit = new CashDeposit(CashDeposit.CASH_VAULT, ONE_MILLION);

        // when
        var thrown = catchThrowable(() -> deposit.requireCustomerAccount(glAccount()));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_TYPE_NOT_ALLOWED);
    }

    @Test
    void should_allowDeposit_when_targetIsCustomerAccount() {
        // given
        var deposit = new CashDeposit(ACCOUNT_NO, ONE_MILLION);

        // when
        var thrown = catchThrowable(() -> deposit.requireCustomerAccount(customerAccount()));

        // then
        assertThat(thrown).isNull();
    }

    @Test
    void should_beRecordedBy_when_entriesMatchAccountAndAmountIgnoringScale() {
        // given: Oracle tra 1000000 (scale 0) cho NUMBER(19,2)
        var deposit = new CashDeposit(ACCOUNT_NO, ONE_MILLION);
        var entries = depositEntries(ACCOUNT_NO, new BigDecimal("1000000"));

        // when / then
        assertThat(deposit.isRecordedBy(entries)).isTrue();
        assertThat(deposit.creditEntryOf(entries).getAccountNo()).isEqualTo(ACCOUNT_NO);
    }

    @Test
    void should_notBeRecordedBy_when_amountDiffers() {
        // given
        var deposit = new CashDeposit(ACCOUNT_NO, ONE_MILLION);

        // when / then
        assertThat(deposit.isRecordedBy(depositEntries(ACCOUNT_NO, new BigDecimal("999999.99"))))
                .isFalse();
    }

    @Test
    void should_notBeRecordedBy_when_accountDiffers() {
        // given
        var deposit = new CashDeposit(ACCOUNT_NO, ONE_MILLION);

        // when / then
        assertThat(deposit.isRecordedBy(depositEntries("100000000002", ONE_MILLION)))
                .isFalse();
    }

    private static List<LedgerEntry> depositEntries(String accountNo, BigDecimal amount) {
        var journal = Journal.create(
                ReferenceType.DEPOSIT,
                "key-1",
                "Nap tien",
                new CashDeposit(accountNo, amount).toPostingLines(),
                CLOCK.instant());
        ReflectionTestUtils.setField(journal, "id", 1L);
        var customer = customerAccount();
        ReflectionTestUtils.setField(customer, "accountNo", accountNo);
        // LedgerEntry.post dua so tien ve scale cua PostingLine; gan lai de gia lap gia tri Oracle tra ve
        var debit = LedgerEntry.post(
                journal, glAccount(), new PostingLine(CashDeposit.CASH_VAULT, EntryDirection.DEBIT, amount));
        var credit = LedgerEntry.post(journal, customer, new PostingLine(accountNo, EntryDirection.CREDIT, amount));
        ReflectionTestUtils.setField(debit, "amount", amount);
        ReflectionTestUtils.setField(credit, "amount", amount);
        return List.of(debit, credit);
    }

    private static Account customerAccount() {
        return Account.open(ACCOUNT_NO, new AccountHolder("C000000001", "ACTIVE"), AccountType.CURRENT, 0, CLOCK);
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
