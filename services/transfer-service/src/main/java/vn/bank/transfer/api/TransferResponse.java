package vn.bank.transfer.api;

import java.math.BigDecimal;
import java.time.Instant;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.domain.TransferType;

/**
 * {@code fee}: phi du kien tinh luc tao lenh, chua thu cho den khi lenh duoc thuc hien. {@code failureCode}: ma loi khi
 * lenh FAILED (vi du {@code INSUFFICIENT_BALANCE}), con lai null.
 */
public record TransferResponse(
        String transferId,
        TransferType type,
        TransferStatus status,
        String failureCode,
        String sourceAccount,
        String destAccount,
        String destBankCode,
        BigDecimal amount,
        BigDecimal fee,
        String description,
        Instant createdAt) {}
