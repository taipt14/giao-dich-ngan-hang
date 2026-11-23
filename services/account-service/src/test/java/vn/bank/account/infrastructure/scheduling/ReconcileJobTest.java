package vn.bank.account.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.bank.account.application.RunReconcileService;

@ExtendWith(OutputCaptureExtension.class)
class ReconcileJobTest {

    private final RunReconcileService runReconcileService = mock(RunReconcileService.class);
    private final ReconcileJob job = new ReconcileJob(runReconcileService);

    @Test
    void should_reconcileToday_when_scheduleFires() {
        // when
        job.run();

        // then
        verify(runReconcileService).runToday();
    }

    @Test
    void should_logErrorAndNotThrow_when_reconcileFails(CapturedOutput output) {
        // given
        when(runReconcileService.runToday()).thenThrow(new IllegalStateException("ORA-20010"));

        // when
        var thrown = catchThrowable(job::run);

        // then
        assertThat(thrown).isNull();
        assertThat(output.getOut()).contains("ERROR").contains("Scheduled reconcile failed");
    }
}
