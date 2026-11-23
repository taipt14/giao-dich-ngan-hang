package vn.bank.account.application;

import java.time.LocalDate;
import java.util.List;
import vn.bank.account.domain.ReconcileResult;

/** Ket qua lan doi soat moi nhat cua {@code businessDate}: cac loi con OPEN, rong neu so cai khop. */
public record ReconcileReport(LocalDate businessDate, List<ReconcileResult> errors) {

    public ReconcileReport {
        errors = List.copyOf(errors);
    }
}
