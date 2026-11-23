package vn.bank.common.events.customer;

import java.time.Instant;

/**
 * Payload event {@code CustomerCreated} tren topic {@code customer.events}, key = {@code cif}. Khong chua so CCCD: ben
 * nhan chi can CIF, trang thai va thong tin lien he.
 */
public record CustomerCreated(
        String cif, String fullName, String phoneNumber, String email, String status, Instant createdAt) {

    public static final String EVENT_TYPE = "CustomerCreated";
}
