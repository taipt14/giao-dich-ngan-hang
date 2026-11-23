package vn.bank.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.bank.transfer.domain.FeeSchedule;
import vn.bank.transfer.domain.TransferType;
import vn.bank.transfer.infrastructure.persistence.FeeScheduleRepository;

class TransferFeeServiceTest {

    private static final LocalDate NEW_SCHEDULE_FROM = LocalDate.of(2026, 11, 24);

    private final FeeScheduleRepository feeScheduleRepository = mock(FeeScheduleRepository.class);

    @Test
    void should_useCurrentSchedule_when_newScheduleStartsTomorrowInBankZone() {
        // given: 16:59 UTC ngay 23/11 = 23:59 ngay 23/11 gio Viet Nam
        givenCurrentAndNewInterbankSchedules();
        var service = new TransferFeeService(feeScheduleRepository, clockAt("2026-11-23T16:59:00Z"));

        // when
        var fee = service.calculateFee(TransferType.INTERBANK, new BigDecimal("20000000"));

        // then
        assertThat(fee).isEqualByComparingTo("4000");
    }

    @Test
    void should_useNewSchedule_when_utcDayIsStillYesterdayButBankDayReachedEffectiveDate() {
        // given: 17:00 UTC ngay 23/11 = 00:00 ngay 24/11 gio Viet Nam
        givenCurrentAndNewInterbankSchedules();
        var service = new TransferFeeService(feeScheduleRepository, clockAt("2026-11-23T17:00:00Z"));

        // when
        var fee = service.calculateFee(TransferType.INTERBANK, new BigDecimal("20000000"));

        // then
        assertThat(fee).isEqualByComparingTo("10000");
    }

    private void givenCurrentAndNewInterbankSchedules() {
        when(feeScheduleRepository.findByTransferType(TransferType.INTERBANK))
                .thenReturn(List.of(
                        schedule("0", "500000", "0", "0", "0", LocalDate.of(2026, 1, 1)),
                        schedule("500000", null, "0.0002", "2200", "11000", LocalDate.of(2026, 1, 1)),
                        schedule("0", null, "0.0005", "5000", "50000", NEW_SCHEDULE_FROM)));
    }

    private static Clock clockAt(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    private static FeeSchedule schedule(
            String minAmount, String maxAmount, String rate, String minFee, String maxFee, LocalDate effectiveFrom) {
        var tier = new FeeSchedule.Tier(
                TransferType.INTERBANK,
                new BigDecimal(minAmount),
                maxAmount == null ? null : new BigDecimal(maxAmount),
                new BigDecimal(rate),
                new BigDecimal(minFee),
                new BigDecimal(maxFee));
        return FeeSchedule.of(tier, effectiveFrom);
    }
}
