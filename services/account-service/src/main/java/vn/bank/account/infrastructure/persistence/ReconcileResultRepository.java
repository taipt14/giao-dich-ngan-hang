package vn.bank.account.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.query.Procedure;
import org.springframework.data.repository.query.Param;
import vn.bank.account.domain.ReconcileResult;
import vn.bank.account.domain.ReconcileStatus;

public interface ReconcileResultRepository extends JpaRepository<ReconcileResult, Long> {

    /** Goi {@code PKG_RECONCILE.RUN}: ghi/cap nhat RECONCILE_RESULT trong transaction hien tai, khong tu COMMIT. */
    @Procedure(procedureName = "PKG_RECONCILE.RUN")
    void runReconcile(@Param("p_business_date") LocalDate businessDate);

    // Dung index UK_RECONCILE_RESULT_DATE_TYPE_ACC_JRN (BUSINESS_DATE dung dau)
    List<ReconcileResult> findByBusinessDateAndStatusOrderByIdAsc(LocalDate businessDate, ReconcileStatus status);
}
