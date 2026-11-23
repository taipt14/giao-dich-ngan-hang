package vn.bank.account.application;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.bank.account.domain.BankZone;
import vn.bank.account.domain.ReconcileStatus;
import vn.bank.account.infrastructure.persistence.ReconcileResultRepository;

/**
 * Doi soat cuoi ngay bang {@code PKG_RECONCILE.RUN} (BANK-034). Chay lai cung ngay ghi de ket qua cu: procedure MERGE
 * theo khoa nghiep vu va danh dau RESOLVED loi khong con, nen bao cao chi gom loi cua lan chay nay.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunReconcileService {

    // Procedure quet toan bo LEDGER_ENTRY va cho khoa RECONCILE_RESULT neu dang co lan chay khac: lau hon posting
    private static final int RECONCILE_TIMEOUT_SECONDS = 300;

    private final ReconcileResultRepository reconcileResultRepository;
    private final Clock clock;

    /** Doi soat ngay nghiep vu hien tai theo gio {@link BankZone#ZONE} (lich chay luc 23:55). */
    @Transactional(timeout = RECONCILE_TIMEOUT_SECONDS)
    public ReconcileReport runToday() {
        return reconcile(LocalDate.now(clock.withZone(BankZone.ZONE)));
    }

    @Transactional(timeout = RECONCILE_TIMEOUT_SECONDS)
    public ReconcileReport run(LocalDate businessDate) {
        return reconcile(businessDate);
    }

    private ReconcileReport reconcile(LocalDate businessDate) {
        reconcileResultRepository.runReconcile(businessDate);
        var errors =
                reconcileResultRepository.findByBusinessDateAndStatusOrderByIdAsc(businessDate, ReconcileStatus.OPEN);
        var report = new ReconcileReport(businessDate, errors);
        logReport(report);
        return report;
    }

    // Lech so cai can nguoi kiem tra (muc 13 convention): moi loi mot dong ERROR, so tai khoan da che
    private static void logReport(ReconcileReport report) {
        if (report.errors().isEmpty()) {
            log.info("Reconcile {} completed: no error", report.businessDate());
            return;
        }
        log.error(
                "Reconcile {} found {} error(s)",
                report.businessDate(),
                report.errors().size());
        for (var error : report.errors()) {
            log.error(
                    "Reconcile {} {} on {}: expected {}, actual {}",
                    report.businessDate(),
                    error.getErrorType(),
                    error.describeTarget(),
                    error.getExpectedValue().toPlainString(),
                    error.getActualValue().toPlainString());
        }
    }
}
