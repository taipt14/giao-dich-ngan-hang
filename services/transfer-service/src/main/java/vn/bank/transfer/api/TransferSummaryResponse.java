package vn.bank.transfer.api;

import java.math.BigDecimal;
import java.time.Instant;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.domain.TransferType;

/** Mot dong trong danh sach lenh. {@code destAccount} da che mot phan, vi du {@code 1000****5678}. */
public record TransferSummaryResponse(
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
