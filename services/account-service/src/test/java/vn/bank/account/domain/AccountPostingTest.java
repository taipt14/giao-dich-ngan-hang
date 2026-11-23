package vn.bank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.bank.common.error.BusinessException;

/** Ghi No/Co vao tai khoan: quy tac trang thai va so du (BANK-031). */
class AccountPostingTest {

    private static final Instant OPENED_AT = Instant.parse("2026-11-21T03:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(OPENED_AT, ZoneOffset.UTC);
    private static final AccountHolder ACTIVE_HOLDER = new AccountHolder("C000000001", "ACTIVE");

    @Test
    void should_increaseBalance_when_creditActiveCustomerAccount() {
        // given
        var account = customerAccount();

        // when
        account.credit(new BigDecimal("1000000.00"), NOW);

        // then
        assertThat(account.getBalance()).isEqualByComparingTo("1000000.00");
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("1000000.00");
        assertThat(account.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void should_decreaseBalance_when_debitWithinBalance() {
        // given
        var account = customerAccount();
        account.credit(new BigDecimal("1000000.00"), NOW);

        // when
        account.post(EntryDirection.DEBIT, new BigDecimal("300000.00"), NOW);

        // then
        assertThat(account.getBalance()).isEqualByComparingTo("700000.00");
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("700000.00");
    }

    @Test
    void should_rejectDebit_when_balanceInsufficient() {
        // given
        var account = customerAccount();
        account.credit(new BigDecimal("100.00"), NOW);

        // when
        var thrown = catchThrowable(() -> account.debit(new BigDecimal("100.01"), NOW));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.INSUFFICIENT_BALANCE);
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void should_rejectCredit_when_customerAccountLocked() {
        // given
        var account = customerAccount();
        account.changeStatus(AccountStatus.LOCKED, "Nghi ngo gian lan", CLOCK);

        // when
        var thrown = catchThrowable(() -> account.credit(new BigDecimal("100.00"), NOW));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void should_rejectDebit_when_customerAccountLocked() {
        // given
        var account = customerAccount();
        account.credit(new BigDecimal("100.00"), NOW);
        account.changeStatus(AccountStatus.LOCKED, "Nghi ngo gian lan", CLOCK);

        // when
        var thrown = catchThrowable(() -> account.debit(new BigDecimal("50.00"), NOW));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    void should_allowNegativeBalance_when_debitGlAccount() {
        // given
        var account = glAccount();

        // when
        account.debit(new BigDecimal("1000000.00"), NOW);

        // then
        assertThat(account.getBalance()).isEqualByComparingTo("-1000000.00");
    }

    @Test
    void should_rejectAmount_when_notPositive() {
        // given
        var account = customerAccount();

        // when
        var thrown = catchThrowable(() -> account.credit(BigDecimal.ZERO, NOW));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.INVALID_AMOUNT);
    }

    private static Account customerAccount() {
        return Account.open("100000000007", ACTIVE_HOLDER, AccountType.CURRENT, 0, CLOCK);
    }

    // Tai khoan GL chi tao bang migration, test dung reflection de co entity GL
    private static Account glAccount() {
        var account = customerAccount();
        ReflectionTestUtils.setField(account, "accountNo", "CASH_VAULT");
        ReflectionTestUtils.setField(account, "cif", null);
        ReflectionTestUtils.setField(account, "accountType", AccountType.GL);
        return account;
    }
}
