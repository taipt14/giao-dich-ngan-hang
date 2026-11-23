package vn.bank.customer.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateCustomerRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotNull @Past LocalDate dateOfBirth,

        @NotNull @Pattern(regexp = ID_NUMBER_PATTERN, message = "must be 12 digits")
        String idNumber,

        @NotNull @Pattern(regexp = PHONE_NUMBER_PATTERN, message = "must be a 10-digit Vietnamese phone number")
        String phoneNumber,

        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 255) String address) {

    static final String ID_NUMBER_PATTERN = "\\d{12}";
    // So dien thoai Viet Nam: 10 so, bat dau bang 0
    static final String PHONE_NUMBER_PATTERN = "0\\d{9}";
}
