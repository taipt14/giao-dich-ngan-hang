package vn.bank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import vn.bank.common.error.BusinessException;
import vn.bank.common.error.ErrorCode;

class TransferLimitConfigTest {

    private static final TransferLimitConfig STANDARD = TransferLimitConfig.of(
            CustomerType.STANDARD, new BigDecimal("100000000.00"), new BigDecimal("500000000.00"));

    @Test
    void should_rejectWithPerTransactionLimitExceeded_when_amountIs100000001() {
        // when
        var thrown = catchThrowable(() -> STANDARD.requireWithinPerTransactionLimit(new BigDecimal("100000001")));

        // then
        assertErrorCode(thrown, TransferErrorCode.PER_TRANSACTION_LIMIT_EXCEEDED);
    }

    @Test
    void should_accept_when_amountEqualsPerTransactionLimit() {
        // when / then
        assertThatCode(() -> STANDARD.requireWithinPerTransactionLimit(new BigDecimal("100000000")))
                .doesNotThrowAnyException();
    }

    @Test
    void should_rejectWithDailyLimitExceeded_when_450MillionUsedAnd60MillionMore() {
        // when
        var thrown = catchThrowable(
                () -> STANDARD.requireWithinDailyLimit(new BigDecimal("450000000"), new BigDecimal("60000000")));

        // then
        assertErrorCode(thrown, TransferErrorCode.DAILY_LIMIT_EXCEEDED);
    }

    @Test
    void should_accept_when_totalReachesDailyLimitExactly() {
        // when / then
        assertThatCode(() ->
                        STANDARD.requireWithinDailyLimit(new BigDecimal("450000000"), new BigDecimal("50000000.00")))
                .doesNotThrowAnyException();
    }

    @Test
    void should_rejectConfig_when_dailyLimitBelowPerTransactionLimit() {
        // when
        var thrown = catchThrowable(() -> TransferLimitConfig.of(
                CustomerType.STANDARD, new BigDecimal("100000000"), new BigDecimal("99999999.99")));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertErrorCode(Throwable thrown, ErrorCode code) {
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }
}
