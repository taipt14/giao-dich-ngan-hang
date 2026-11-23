package vn.bank.account.api;

import java.math.BigDecimal;
import vn.bank.account.domain.EntryDirection;

public record LedgerEntryResponse(
        Long entryId, String accountNo, EntryDirection direction, BigDecimal amount, BigDecimal balanceAfter) {}
