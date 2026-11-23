package vn.bank.common.events.transfer;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload event {@code TransferFailed} tren topic {@code transfer.events}, key = {@code transferId}. Phat khi lenh
 * chuyen khoan ket thuc khong thanh: {@code status} la {@code FAILED} (tien chua bi tru, {@code failureCode} cho biet
 * ly do, vi du {@code INSUFFICIENT_BALANCE}, {@code OTP_MAX_ATTEMPTS}) hoac {@code REVERSED} (da hoan tien, BANK-053).
 */
public record TransferFailed(
        String transferId,
        String type,
        String cif,
        String sourceAccount,
        String destAccount,
        String destBankCode,
        BigDecimal amount,
        BigDecimal fee,
        String description,
        String status,
        String failureCode,
        Instant failedAt) {

    public static final String EVENT_TYPE = "TransferFailed";
}
