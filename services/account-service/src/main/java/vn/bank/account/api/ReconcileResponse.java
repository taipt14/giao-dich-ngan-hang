package vn.bank.account.api;

import java.time.LocalDate;
import java.util.List;

/** Ket qua lan doi soat vua chay: {@code errors} rong khi so cai khop. */
public record ReconcileResponse(LocalDate businessDate, int errorCount, List<ReconcileErrorResponse> errors) {}
