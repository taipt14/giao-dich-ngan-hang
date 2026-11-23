package vn.bank.common.events.account;

import java.time.Instant;

/**
 * Payload event {@code AccountOpened} tren topic {@code account.events}, key = {@code accountNo}. Tai khoan moi mo luon
 * co so du 0 nen khong kem so du.
 */
public record AccountOpened(
        String accountNo, String cif, String accountType, String currency, String status, Instant openedAt) {

    public static final String EVENT_TYPE = "AccountOpened";
}
