package vn.bank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import vn.bank.common.error.BusinessException;

class TransferPeriodTest {

    private static final LocalDate NOV_20 = LocalDate.parse("2026-11-20");
    private static final LocalDate NOV_21 = LocalDate.parse("2026-11-21");

    @Test
    void should_coverWholeVietnamBusinessDays_when_fromAndToGiven() {
        // given / when
        var period = TransferPeriod.of(NOV_20, NOV_21);

        // then: 00:00 gio Viet Nam (UTC+7) ngay 20 den truoc 00:00 ngay 22
        assertThat(period.startInclusive()).isEqualTo(Instant.parse("2026-11-19T17:00:00Z"));
        assertThat(period.endExclusive()).isEqualTo(Instant.parse("2026-11-21T17:00:00Z"));
    }

    @Test
    void should_acceptSingleDay_when_fromEqualsTo() {
        // given / when
        var period = TransferPeriod.of(NOV_20, NOV_20);

        // then
        assertThat(period.startInclusive()).isEqualTo(Instant.parse("2026-11-19T17:00:00Z"));
        assertThat(period.endExclusive()).isEqualTo(Instant.parse("2026-11-20T17:00:00Z"));
    }

    @Test
    void should_haveNoBounds_when_fromAndToMissing() {
        // given / when
        var period = TransferPeriod.of(null, null);

        // then
        assertThat(period.startInclusive()).isNull();
        assertThat(period.endExclusive()).isNull();
    }

    @Test
    void should_haveOnlyOneBound_when_onlyOneDateGiven() {
        // given / when
        var fromOnly = TransferPeriod.of(NOV_20, null);
        var toOnly = TransferPeriod.of(null, NOV_21);

        // then
        assertThat(fromOnly.startInclusive()).isEqualTo(Instant.parse("2026-11-19T17:00:00Z"));
        assertThat(fromOnly.endExclusive()).isNull();
        assertThat(toOnly.startInclusive()).isNull();
        assertThat(toOnly.endExclusive()).isEqualTo(Instant.parse("2026-11-21T17:00:00Z"));
    }

    @Test
    void should_rejectWithTransferRangeInvalid_when_fromAfterTo() {
        // given / when
        var thrown = catchThrowable(() -> TransferPeriod.of(NOV_21, NOV_20));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.TRANSFER_RANGE_INVALID);
    }
}
