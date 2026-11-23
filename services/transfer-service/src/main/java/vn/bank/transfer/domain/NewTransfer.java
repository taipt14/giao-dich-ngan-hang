package vn.bank.transfer.domain;

import java.math.BigDecimal;

/**
 * Thong tin de tao lenh chuyen khoan. {@code destBankCode} null → lenh noi bo, khac null → lien ngan hang.
 * {@code description} co the null.
 */
public record NewTransfer(
        String cif,
        String idempotencyKey,
        String sourceAccount,
        String destAccount,
        String destBankCode,
        BigDecimal amount,
        BigDecimal fee,
        String description) {

    public TransferType type() {
        return destBankCode == null ? TransferType.INTERNAL : TransferType.INTERBANK;
    }
}
