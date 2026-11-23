package vn.bank.common.events.customer;

import java.time.Instant;

/**
 * Payload event {@code CustomerStatusChanged} tren topic {@code customer.events}, key = {@code cif}. Phat khi ADMIN
 * doi trang thai khach hang (ACTIVE/LOCKED), kem ly do.
 */
public record CustomerStatusChanged(
        String cif, String previousStatus, String status, String reason, Instant changedAt) {

    public static final String EVENT_TYPE = "CustomerStatusChanged";
}
