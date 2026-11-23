package vn.bank.account.api;

import java.math.BigDecimal;
import java.time.Instant;

/** Lan nap tien da ghi so (moi hoac lan nap cu cung Idempotency-Key); {@code balanceAfter}: so du ngay sau lan nap. */
public record DepositResponse(
        Long journalId,
        String idempotencyKey,
        String accountNo,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        Instant createdAt) {}
