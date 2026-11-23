package vn.bank.common.events.account;

import java.time.Instant;

/**
 * Payload event {@code AccountStatusChanged} tren topic {@code account.events}, key = {@code accountNo}. Phat khi tai
 * khoan doi trang thai (ACTIVE/LOCKED/CLOSED) do ADMIN hoac do khach hang bi khoa, kem ly do.
 */
public record AccountStatusChanged(
        String accountNo, String cif, String previousStatus, String status, String reason, Instant changedAt) {

    public static final String EVENT_TYPE = "AccountStatusChanged";
}
