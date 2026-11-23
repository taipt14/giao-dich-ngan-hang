package vn.bank.account.api;

import java.math.BigDecimal;

/** {@code balance}: so du thuc te; {@code availableBalance}: so du kha dung. */
public record AccountBalanceResponse(
        String accountNo, String currency, BigDecimal balance, BigDecimal availableBalance) {}
