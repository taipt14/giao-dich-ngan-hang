package vn.bank.customer.domain;

import java.time.LocalDate;

/** Thong tin ho so khi dang ky khach hang, da qua kiem tra dinh dang o tang api. */
public record CustomerProfile(
        String fullName, LocalDate dateOfBirth, String idNumber, String phoneNumber, String email, String address) {}
