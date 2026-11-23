package vn.bank.account.api;

import java.math.BigDecimal;
import vn.bank.account.domain.ReconcileErrorType;

/**
 * Mot loi doi soat. BALANCE_MISMATCH: {@code accountNo}, mong doi = tong so cai, thuc te = BALANCE. JOURNAL_UNBALANCED:
 * {@code journalId}, mong doi = tong No, thuc te = tong Co.
 */
public record ReconcileErrorResponse(
        ReconcileErrorType errorType,
        String accountNo,
        Long journalId,
        BigDecimal expectedValue,
        BigDecimal actualValue) {}
