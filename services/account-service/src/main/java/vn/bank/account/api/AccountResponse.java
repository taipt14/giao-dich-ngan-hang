package vn.bank.account.api;

import java.math.BigDecimal;
import java.time.Instant;
import vn.bank.account.domain.AccountStatus;
import vn.bank.account.domain.AccountType;

public record AccountResponse(
        String accountNo,
        String cif,
        AccountType accountType,
        String currency,
        BigDecimal balance,
        BigDecimal availableBalance,
        AccountStatus status,
        Instant createdAt) {}
