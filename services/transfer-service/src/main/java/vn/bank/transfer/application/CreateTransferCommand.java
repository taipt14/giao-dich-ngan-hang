package vn.bank.transfer.application;

import java.math.BigDecimal;
import vn.bank.transfer.domain.NewTransfer;

/** Yeu cau tao lenh cua khach hang {@code cif}; {@code destBankCode} null → noi bo, {@code description} co the null. */
public record CreateTransferCommand(
        String cif,
        String idempotencyKey,
        String sourceAccount,
        String destAccount,
        String destBankCode,
        BigDecimal amount,
        String description) {

    NewTransfer toNewTransfer(BigDecimal fee) {
        return new NewTransfer(cif, idempotencyKey, sourceAccount, destAccount, destBankCode, amount, fee, description);
    }
}
