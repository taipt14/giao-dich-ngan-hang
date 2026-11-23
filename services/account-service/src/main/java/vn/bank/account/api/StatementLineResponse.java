package vn.bank.account.api;

import java.math.BigDecimal;
import java.time.Instant;
import vn.bank.account.domain.EntryDirection;
import vn.bank.account.domain.ReferenceType;

/** {@code amount}: so tien co dau (CREDIT duong, DEBIT am); {@code balanceAfter}: so du ngay sau giao dich. */
public record StatementLineResponse(
        Long entryId,
        Instant createdAt,
        ReferenceType referenceType,
        String description,
        EntryDirection direction,
        BigDecimal amount,
        BigDecimal balanceAfter) {}
