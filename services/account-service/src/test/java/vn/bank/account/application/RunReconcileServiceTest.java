package vn.bank.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.bank.account.domain.ReconcileErrorType;
import vn.bank.account.domain.ReconcileResult;
import vn.bank.account.domain.ReconcileStatus;
import vn.bank.account.infrastructure.persistence.ReconcileResultRepository;

@ExtendWith(OutputCaptureExtension.class)
class RunReconcileServiceTest {

    // 23:55 ngay 2026-11-23 gio Viet Nam (UTC+7) = 16:55 UTC cung ngay
    private static final Instant SCHEDULED_AT = Instant.parse("2026-11-23T16:55:00Z");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2026-11-23");

    private final ReconcileResultRepository repository = mock(ReconcileResultRepository.class);

    @Test
    void should_reconcileVietnamBusinessDate_when_runTodayAtScheduledTime() {
        // given
        var service = serviceAt(SCHEDULED_AT);
        givenOpenErrors(BUSINESS_DATE, List.of());

        // when
        var report = service.runToday();

        // then
        assertThat(report.businessDate()).isEqualTo(BUSINESS_DATE);
        var order = inOrder(repository);
        order.verify(repository).runReconcile(BUSINESS_DATE);
        order.verify(repository).findByBusinessDateAndStatusOrderByIdAsc(BUSINESS_DATE, ReconcileStatus.OPEN);
    }

    @Test
    void should_useVietnamDateNotUtcDate_when_utcIsStillPreviousDay() {
        // given: 2026-11-23T17:30Z la 00:30 ngay 2026-11-24 gio VN
        var service = serviceAt(Instant.parse("2026-11-23T17:30:00Z"));
        var vietnamDate = LocalDate.parse("2026-11-24");
        givenOpenErrors(vietnamDate, List.of());

        // when
        var report = service.runToday();

        // then
        assertThat(report.businessDate()).isEqualTo(vietnamDate);
    }

    @Test
    void should_logErrorWithMaskedAccount_when_reconcileFindsErrors(CapturedOutput output) {
        // given
        var service = serviceAt(SCHEDULED_AT);
        var mismatch = error(ReconcileErrorType.BALANCE_MISMATCH, "100012345678", null, "1000.00", "1234.00");
        var unbalanced = error(ReconcileErrorType.JOURNAL_UNBALANCED, null, 42L, "0.00", "100.00");
        givenOpenErrors(BUSINESS_DATE, List.of(mismatch, unbalanced));

        // when
        var report = service.run(BUSINESS_DATE);

        // then
        assertThat(report.errors()).containsExactly(mismatch, unbalanced);
        assertThat(output.getOut())
                .contains("ERROR")
                .contains("Reconcile 2026-11-23 found 2 error(s)")
                .contains("BALANCE_MISMATCH on account 1000****5678: expected 1000.00, actual 1234.00")
                .contains("JOURNAL_UNBALANCED on journal 42: expected 0.00, actual 100.00")
                .doesNotContain("100012345678");
    }

    @Test
    void should_notLogError_when_noReconcileError(CapturedOutput output) {
        // given
        var service = serviceAt(SCHEDULED_AT);
        givenOpenErrors(BUSINESS_DATE, List.of());

        // when
        var report = service.run(BUSINESS_DATE);

        // then
        assertThat(report.errors()).isEmpty();
        assertThat(output.getOut())
                .contains("Reconcile 2026-11-23 completed: no error")
                .doesNotContain("ERROR");
    }

    private RunReconcileService serviceAt(Instant now) {
        return new RunReconcileService(repository, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void givenOpenErrors(LocalDate date, List<ReconcileResult> errors) {
        when(repository.findByBusinessDateAndStatusOrderByIdAsc(date, ReconcileStatus.OPEN))
                .thenReturn(errors);
    }

    // Entity chi do PKG_RECONCILE ghi, khong co factory: dung reflection nhu Hibernate khi nap tu DB
    private static ReconcileResult error(
            ReconcileErrorType type, String accountNo, Long journalId, String expected, String actual) {
        var result = BeanUtils.instantiateClass(ReconcileResult.class);
        ReflectionTestUtils.setField(result, "errorType", type);
        ReflectionTestUtils.setField(result, "accountNo", accountNo);
        ReflectionTestUtils.setField(result, "journalId", journalId);
        ReflectionTestUtils.setField(result, "expectedValue", new BigDecimal(expected));
        ReflectionTestUtils.setField(result, "actualValue", new BigDecimal(actual));
        return result;
    }
}
