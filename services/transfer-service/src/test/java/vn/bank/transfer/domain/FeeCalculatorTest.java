package vn.bank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FeeCalculatorTest {

    private static final LocalDate CURRENT_FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    // Bieu phi hien hanh (giong seed V4): noi bo mien phi; lien ngan hang < 500.000 mien phi, tu 500.000 la 0,02%
    private static final List<FeeSchedule> CURRENT = List.of(
            schedule(TransferType.INTERNAL, "0", null, "0", "0", "0", CURRENT_FROM),
            schedule(TransferType.INTERBANK, "0", "500000", "0", "0", "0", CURRENT_FROM),
            schedule(TransferType.INTERBANK, "500000", null, "0.0002", "2200", "11000", CURRENT_FROM));

    @ParameterizedTest
    @CsvSource({"499999, 0", "500000, 2200", "20000000, 4000", "100000000, 11000"})
    void should_chargeInterbankFeeByTier_when_amountAtThresholds(String amount, String expectedFee) {
        // when
        var fee = FeeCalculator.calculate(CURRENT, TransferType.INTERBANK, new BigDecimal(amount), TODAY);

        // then
        assertThat(fee).isEqualByComparingTo(expectedFee);
    }

    @Test
    void should_chargeNoFee_when_internalTransfer() {
        // when
        var fee = FeeCalculator.calculate(CURRENT, TransferType.INTERNAL, new BigDecimal("100000000"), TODAY);

        // then
        assertThat(fee).isEqualByComparingTo("0");
    }

    @Test
    void should_chargeNoFee_when_interbankAmountJustBelow500000WithCents() {
        // when
        var fee = FeeCalculator.calculate(CURRENT, TransferType.INTERBANK, new BigDecimal("499999.99"), TODAY);

        // then
        assertThat(fee).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    // 0,02%: 12.347.499 → 2.469,4998; 12.347.500 → 2.469,5; 12.347.502,50 → 2.469,5005
    @CsvSource({"12347499, 2469", "12347500, 2470", "12347502.50, 2470"})
    void should_roundFeeHalfUpToWholeDong_when_rateGivesFraction(String amount, String expectedFee) {
        // when
        var fee = FeeCalculator.calculate(CURRENT, TransferType.INTERBANK, new BigDecimal(amount), TODAY);

        // then
        assertThat(fee).isEqualTo(new BigDecimal(expectedFee));
    }

    @Test
    void should_useCurrentSchedule_when_newScheduleEffectiveInFuture() {
        // given: bieu phi moi 0,05% (5.000 - 50.000) tu ngay mai
        var schedules = withNewInterbankScheduleFrom(TOMORROW);

        // when
        var fee = FeeCalculator.calculate(schedules, TransferType.INTERBANK, new BigDecimal("20000000"), TODAY);

        // then
        assertThat(fee).isEqualByComparingTo("4000");
    }

    @Test
    void should_useNewSchedule_when_newScheduleEffectiveFromToday() {
        // given
        var schedules = withNewInterbankScheduleFrom(TODAY);

        // when
        var fee = FeeCalculator.calculate(schedules, TransferType.INTERBANK, new BigDecimal("20000000"), TODAY);

        // then: bieu phi moi chi co mot khoang, khong con muc mien phi duoi 500.000
        assertThat(fee).isEqualByComparingTo("10000");
        assertThat(FeeCalculator.calculate(schedules, TransferType.INTERBANK, new BigDecimal("100000"), TODAY))
                .isEqualByComparingTo("5000");
    }

    @Test
    void should_failWithConfigError_when_noScheduleEffectiveYet() {
        // given
        var schedules = List.of(schedule(TransferType.INTERBANK, "0", null, "0", "0", "0", TOMORROW));

        // when
        var thrown = catchThrowable(
                () -> FeeCalculator.calculate(schedules, TransferType.INTERBANK, new BigDecimal("1000000"), TODAY));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_failWithConfigError_when_scheduleDoesNotCoverAmount() {
        // given
        var schedules = List.of(schedule(TransferType.INTERBANK, "0", "500000", "0", "0", "0", CURRENT_FROM));

        // when
        var thrown = catchThrowable(
                () -> FeeCalculator.calculate(schedules, TransferType.INTERBANK, new BigDecimal("500000"), TODAY));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_rejectTier_when_maxFeeBelowMinFee() {
        // when
        var thrown = catchThrowable(
                () -> schedule(TransferType.INTERBANK, "0", null, "0.0002", "11000", "2200", CURRENT_FROM));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<FeeSchedule> withNewInterbankScheduleFrom(LocalDate effectiveFrom) {
        var newSchedule = schedule(TransferType.INTERBANK, "0", null, "0.0005", "5000", "50000", effectiveFrom);
        return List.of(CURRENT.get(0), CURRENT.get(1), CURRENT.get(2), newSchedule);
    }

    private static FeeSchedule schedule(
            TransferType type,
            String minAmount,
            String maxAmount,
            String rate,
            String minFee,
            String maxFee,
            LocalDate effectiveFrom) {
        var tier = new FeeSchedule.Tier(
                type,
                new BigDecimal(minAmount),
                maxAmount == null ? null : new BigDecimal(maxAmount),
                new BigDecimal(rate),
                new BigDecimal(minFee),
                new BigDecimal(maxFee));
        return FeeSchedule.of(tier, effectiveFrom);
    }
}
