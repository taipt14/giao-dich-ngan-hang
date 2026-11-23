package vn.bank.account.infrastructure.client;

/** Response cua {@code GET /internal/customers/{cif}} (customer-service, BANK-014). */
record CustomerStatusResponse(String cif, String status) {}
