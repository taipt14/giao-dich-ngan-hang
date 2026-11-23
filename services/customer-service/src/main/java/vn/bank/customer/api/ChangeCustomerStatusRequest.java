package vn.bank.customer.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.bank.customer.domain.CustomerStatus;

public record ChangeCustomerStatusRequest(
        @NotNull CustomerStatus status,
        @NotBlank @Size(max = 255) String reason) {}
