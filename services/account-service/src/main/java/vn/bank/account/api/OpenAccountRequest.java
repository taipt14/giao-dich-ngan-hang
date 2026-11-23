package vn.bank.account.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import vn.bank.account.domain.AccountType;

public record OpenAccountRequest(
        @NotNull @Pattern(regexp = CIF_PATTERN, message = "must be C followed by 9 digits")
        String cif,

        @NotNull AccountType accountType) {

    // Dinh dang CIF cua customer-service (BANK-013)
    static final String CIF_PATTERN = "C\\d{9}";
}
