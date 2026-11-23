package vn.bank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import vn.bank.common.error.BusinessException;

class StatementPeriodTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-11-23");

    @Test
    void should_coverLast30DaysEndingToday_when_noDatesGiven() {
        // when
        var period = StatementPeriod.resolve(null, null, TODAY);

        // then
        assertThat(period.from()).isEqualTo(LocalDate.parse("2026-10-25"));
        assertThat(period.to()).isEqualTo(TODAY);
    }

    @Test
    void should_cover30DaysEndingAtTo_when_onlyToGiven() {
        // when
        var period = StatementPeriod.resolve(null, LocalDate.parse("2026-06-30"), TODAY);

        // then
        assertThat(period.from()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(period.to()).isEqualTo(LocalDate.parse("2026-06-30"));
    }

    @Test
    void should_endToday_when_onlyFromGiven() {
        // when
        var period = StatementPeriod.resolve(LocalDate.parse("2026-11-01"), null, TODAY);

        // then
        assertThat(period.from()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(period.to()).isEqualTo(TODAY);
    }

    @Test
    void should_accept_when_rangeIsExactly90DaysInclusive() {
        // when
        var period = StatementPeriod.resolve(LocalDate.parse("2026-08-26"), TODAY, TODAY);

        // then
        assertThat(period.from()).isEqualTo(LocalDate.parse("2026-08-26"));
    }

    @Test
    void should_rejectStatementRangeTooLarge_when_rangeIs91Days() {
        // when
        var thrown = catchThrowable(() -> StatementPeriod.resolve(LocalDate.parse("2026-08-25"), TODAY, TODAY));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.STATEMENT_RANGE_TOO_LARGE);
    }

    @Test
    void should_rejectStatementRangeTooLarge_when_onlyFromGivenMoreThan90DaysAgo() {
        // when
        var thrown = catchThrowable(() -> StatementPeriod.resolve(LocalDate.parse("2026-08-25"), null, TODAY));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.STATEMENT_RANGE_TOO_LARGE);
    }

    @Test
    void should_rejectStatementRangeInvalid_when_fromAfterTo() {
        // when
        var thrown = catchThrowable(
                () -> StatementPeriod.resolve(LocalDate.parse("2026-11-21"), LocalDate.parse("2026-11-20"), TODAY));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.STATEMENT_RANGE_INVALID);
    }

    @Test
    void should_accept_when_toInFuture() {
        // when
        var period = StatementPeriod.resolve(TODAY, LocalDate.parse("2026-12-31"), TODAY);

        // then
        assertThat(period.to()).isEqualTo(LocalDate.parse("2026-12-31"));
    }

    @Test
    void should_useVietnamMidnightBoundaries_when_convertingToInstants() {
        // given
        var period = new StatementPeriod(LocalDate.parse("2026-11-21"), LocalDate.parse("2026-11-22"));

        // when / then: Asia/Ho_Chi_Minh = UTC+7
        assertThat(period.startInclusive()).isEqualTo(Instant.parse("2026-11-20T17:00:00Z"));
        assertThat(period.endExclusive()).isEqualTo(Instant.parse("2026-11-22T17:00:00Z"));
    }
}
