package vn.bank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import vn.bank.common.error.BusinessException;

class AccountTest {

    private static final Instant NOW = Instant.parse("2026-11-21T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String ACCOUNT_NO = "100000000007";
    private static final String CIF = "C000000001";
    private static final AccountHolder ACTIVE_HOLDER = new AccountHolder(CIF, "ACTIVE");

    @Test
    void should_openActiveVndAccountWithZeroBalance_when_customerActive() {
        // given: khach hang chua co tai khoan nao

        // when
        var account = Account.open(ACCOUNT_NO, ACTIVE_HOLDER, AccountType.CURRENT, 0, CLOCK);

        // then
        assertThat(account.getAccountNo()).isEqualTo(ACCOUNT_NO);
        assertThat(account.getCif()).isEqualTo(CIF);
        assertThat(account.getAccountType()).isEqualTo(AccountType.CURRENT);
        assertThat(account.getCurrency()).isEqualTo("VND");
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getBalance().scale()).isEqualTo(2);
        assertThat(account.getAvailableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getCreatedAt()).isEqualTo(NOW);
        assertThat(account.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void should_openThirdAccount_when_customerHasTwoAccounts() {
        // given
        var existingAccounts = 2;

        // when
        var account = Account.open(ACCOUNT_NO, ACTIVE_HOLDER, AccountType.CURRENT, existingAccounts, CLOCK);

        // then
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void should_rejectOpening_when_customerAlreadyHasThreeAccounts() {
        // given
        var existingAccounts = 3;

        // when
        var thrown = catchThrowable(
                () -> Account.open(ACCOUNT_NO, ACTIVE_HOLDER, AccountType.CURRENT, existingAccounts, CLOCK));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_LIMIT_EXCEEDED);
    }

    @Test
    void should_rejectOpening_when_customerLocked() {
        // given
        var lockedHolder = new AccountHolder(CIF, "LOCKED");

        // when
        var thrown = catchThrowable(() -> Account.open(ACCOUNT_NO, lockedHolder, AccountType.CURRENT, 0, CLOCK));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.CUSTOMER_NOT_ACTIVE);
    }

    @Test
    void should_returnMaskedAccountNo_when_askedForMaskedAccountNo() {
        // given
        var account = Account.open("100012345678", ACTIVE_HOLDER, AccountType.CURRENT, 0, CLOCK);

        // when
        var masked = account.getMaskedAccountNo();

        // then
        assertThat(masked).isEqualTo("1000****5678");
    }

    @Test
    void should_returnBalanceOwnedByHolderOnly_when_convertedToBalance() {
        // given
        var account = Account.open(ACCOUNT_NO, ACTIVE_HOLDER, AccountType.CURRENT, 0, CLOCK);

        // when
        var balance = account.toBalance();

        // then
        assertThat(balance.accountNo()).isEqualTo(ACCOUNT_NO);
        assertThat(balance.currency()).isEqualTo("VND");
        assertThat(balance.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.availableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.isOwnedBy(CIF)).isTrue();
        assertThat(balance.isOwnedBy("C000000002")).isFalse();
    }
}
