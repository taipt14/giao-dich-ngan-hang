package vn.bank.common.events.transfer;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload event {@code TransferCompleted} tren topic {@code transfer.events}, key = {@code transferId}. Phat khi lenh
 * chuyen khoan sang SUCCESS (tien da chuyen). {@code type}: {@code INTERNAL} / {@code INTERBANK};
 * {@code destBankCode} null voi lenh noi bo; {@code fee} la phi da thu (0 neu mien phi).
 */
public record TransferCompleted(
        String transferId,
        String type,
        String cif,
        String sourceAccount,
        String destAccount,
        String destBankCode,
        BigDecimal amount,
        BigDecimal fee,
        String description,
        Instant completedAt) {

    public static final String EVENT_TYPE = "TransferCompleted";
}
