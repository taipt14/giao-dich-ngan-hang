package vn.bank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import vn.bank.common.error.BusinessException;

/** {@link Account#changeStatus}: quy tac doi trang thai cua entity. */
class AccountStatusChangeTest {

    private static final Instant OPENED_AT = Instant.parse("2026-11-21T03:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-22T03:00:00Z");
    private static final Clock OPEN_CLOCK = Clock.fixed(OPENED_AT, ZoneOffset.UTC);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String ACCOUNT_NO = "100000000007";
    private static final String CIF = "C000000001";
    private static final String REASON = "Yeu cau cua khach hang";

    @Test
    void should_lockAndReturnChange_when_activeAccountLocked() {
        // given
        var account = givenAccount(AccountStatus.ACTIVE);

        // when
        var change = account.changeStatus(AccountStatus.LOCKED, REASON, CLOCK);

        // then
        assertThat(account.getStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(account.getUpdatedAt()).isEqualTo(NOW);
        assertThat(change)
                .isEqualTo(new AccountStatusChange(
                        ACCOUNT_NO, CIF, AccountStatus.ACTIVE, AccountStatus.LOCKED, REASON, NOW));
    }

    @Test
    void should_unlock_when_lockedAccountUnlocked() {
        // given
        var account = givenAccount(AccountStatus.LOCKED);

        // when
        var change = account.changeStatus(AccountStatus.ACTIVE, REASON, CLOCK);

        // then
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(change.previousStatus()).isEqualTo(AccountStatus.LOCKED);
    }

    @ParameterizedTest
    @EnumSource(
            value = AccountStatus.class,
            names = {"ACTIVE", "LOCKED"})
    void should_close_when_balanceIsZero(AccountStatus from) {
        // given
        var account = givenAccount(from);

        // when
        account.changeStatus(AccountStatus.CLOSED, REASON, CLOCK);

        // then
        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
    }

    @Test
    void should_rejectClosing_when_balanceNotZero() {
        // given
        var account = givenAccount(AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(account, "balance", new BigDecimal("0.01"));

        // when
        var thrown = catchThrowable(() -> account.changeStatus(AccountStatus.CLOSED, REASON, CLOCK));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_BALANCE_NOT_ZERO);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @ParameterizedTest
    @EnumSource(AccountStatus.class)
    void should_rejectTransition_when_accountClosed(AccountStatus target) {
        // given
        var account = givenAccount(AccountStatus.CLOSED);

        // when
        var thrown = catchThrowable(() -> account.changeStatus(target, REASON, CLOCK));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.INVALID_STATUS_TRANSITION);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(account.getUpdatedAt()).isEqualTo(OPENED_AT);
    }

    @Test
    void should_rejectTransition_when_targetEqualsCurrentStatus() {
        // given
        var account = givenAccount(AccountStatus.LOCKED);

        // when
        var thrown = catchThrowable(() -> account.changeStatus(AccountStatus.LOCKED, REASON, CLOCK));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void should_rejectChange_when_reasonBlank() {
        // given
        var account = givenAccount(AccountStatus.ACTIVE);

        // when
        var thrown = catchThrowable(() -> account.changeStatus(AccountStatus.LOCKED, " ", CLOCK));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    private static Account givenAccount(AccountStatus status) {
        var account = Account.open(ACCOUNT_NO, new AccountHolder(CIF, "ACTIVE"), AccountType.CURRENT, 0, OPEN_CLOCK);
        // Tai khoan moi mo luon ACTIVE; dat thang trang thai bat dau de kiem tung o trong bang chuyen
        ReflectionTestUtils.setField(account, "status", status);
        return account;
    }
}
