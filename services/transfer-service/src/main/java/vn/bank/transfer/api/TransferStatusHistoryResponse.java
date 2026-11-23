package vn.bank.transfer.api;

import java.time.Instant;
import vn.bank.transfer.domain.TransferStatus;

/** Mot lan doi trang thai; {@code previousStatus} null o dong dau tien (lenh vua tao). */
public record TransferStatusHistoryResponse(
        TransferStatus previousStatus, TransferStatus status, String failureCode, Instant changedAt) {}
