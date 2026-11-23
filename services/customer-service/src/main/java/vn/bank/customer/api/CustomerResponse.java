package vn.bank.customer.api;

import java.time.Instant;
import java.time.LocalDate;
import vn.bank.customer.domain.CustomerStatus;

/** {@code idNumber} luon o dang che, vi du {@code 0012******89}. */
public record CustomerResponse(
        String cif,
        String fullName,
        LocalDate dateOfBirth,
        String idNumber,
        String phoneNumber,
        String email,
        String address,
        CustomerStatus status,
        Instant createdAt) {}
