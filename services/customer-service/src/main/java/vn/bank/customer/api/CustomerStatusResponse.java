package vn.bank.customer.api;

import vn.bank.customer.domain.CustomerStatus;

/** Response API noi bo: service khac chi can biet khach hang co ton tai va dang o trang thai nao. */
public record CustomerStatusResponse(String cif, CustomerStatus status) {}
