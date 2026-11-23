package vn.bank.account.api;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.bank.account.application.RunReconcileService;

/** ADMIN chay lai doi soat cuoi ngay thu cong cho ngay nghiep vu {@code date} (ISO, gio VN). */
@RestController
@RequestMapping("/internal/reconcile")
@RequiredArgsConstructor
class ReconcileInternalController {

    private final RunReconcileService runReconcileService;
    private final ReconcileMapper reconcileMapper;

    @PostMapping("/run")
    @RequiresRole(Role.ADMIN)
    ReconcileResponse run(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reconcileMapper.toResponse(runReconcileService.run(date));
    }
}
