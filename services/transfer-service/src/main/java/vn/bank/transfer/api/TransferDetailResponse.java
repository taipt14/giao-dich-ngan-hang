package vn.bank.transfer.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.domain.TransferType;

/** Chi tiet lenh cua chinh khach hang (so tai khoan day du), {@code statusHistory} theo thu tu xay ra. */
public record TransferDetailResponse(
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
        Instant createdAt,
        Instant updatedAt,
        List<TransferStatusHistoryResponse> statusHistory) {}
