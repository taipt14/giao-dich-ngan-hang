package vn.bank.customer.domain;

import java.time.Instant;

/** Ket qua mot lan doi trang thai khach hang, dung de phat event {@code CustomerStatusChanged}. */
public record CustomerStatusChange(
        String cif, CustomerStatus previousStatus, CustomerStatus status, String reason, Instant changedAt) {}
