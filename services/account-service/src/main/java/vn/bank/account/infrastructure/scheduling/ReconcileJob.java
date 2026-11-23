package vn.bank.account.infrastructure.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.bank.account.application.RunReconcileService;
import vn.bank.account.config.ReconcileProperties;
import vn.bank.account.domain.BankZone;

/** Chay doi soat cuoi ngay theo {@code bank.account.reconcile.cron} (gio VN). Ngay nghiep vu do service tinh. */
@Slf4j
@Component
@RequiredArgsConstructor
class ReconcileJob {

    private final RunReconcileService runReconcileService;

    @Scheduled(cron = ReconcileProperties.CRON_PLACEHOLDER, zone = BankZone.ZONE_ID)
    void run() {
        try {
            runReconcileService.runToday();
        } catch (RuntimeException e) {
            // Ranh gioi job: lan chay loi phai co nguoi xem, lan sau van chay theo lich
            log.error("Scheduled reconcile failed", e);
        }
    }
}
