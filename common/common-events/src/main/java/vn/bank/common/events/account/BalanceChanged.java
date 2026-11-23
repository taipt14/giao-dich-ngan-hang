package vn.bank.common.events.account;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload event {@code BalanceChanged} tren topic {@code account.events}, key = {@code accountNo}. Phat cho moi tai
 * khoan khach hang bi doi so du boi mot journal. {@code amount} luon duong, huong tien the hien bang {@code direction}
 * ({@code DEBIT}: tien ra, {@code CREDIT}: tien vao); {@code balanceAfter} la so du ngay sau journal.
 */
public record BalanceChanged(
        String accountNo,
        String cif,
        Long journalId,
        String referenceType,
        String referenceId,
        String direction,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String currency,
        String description,
        Instant changedAt) {

    public static final String EVENT_TYPE = "BalanceChanged";
}
