package vn.bank.customer.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body {@code PATCH /api/v1/customers/{cif}}: chi sua duoc so dien thoai, email, dia chi; truong {@code null} hoac
 * khong gui thi giu nguyen. Ho ten, ngay sinh, CCCD, trang thai duoc khai bao chi de tra 400 {@code VALIDATION_FAILED}
 * chi ro truong nao khong duoc sua, thay vi bi bo qua am tham.
 */
public record UpdateCustomerRequest(
        @Pattern(
                regexp = CreateCustomerRequest.PHONE_NUMBER_PATTERN,
                message = "must be a 10-digit Vietnamese phone number")
        String phoneNumber,

        @Email @Size(max = 254) @Pattern(regexp = NOT_BLANK_PATTERN, message = "must not be blank")
        String email,

        @Size(max = 255) @Pattern(regexp = NOT_BLANK_PATTERN, message = "must not be blank")
        String address,

        @Null(message = NOT_EDITABLE_MESSAGE) String fullName,
        @Null(message = NOT_EDITABLE_MESSAGE) String dateOfBirth,
        @Null(message = NOT_EDITABLE_MESSAGE) String idNumber,
        @Null(message = NOT_EDITABLE_MESSAGE) String status) {

    // Khong dung @NotBlank vi null hop le (giu nguyen); chuoi da gui thi phai co ky tu khac khoang trang
    static final String NOT_BLANK_PATTERN = "(?s).*\\S.*";
    static final String NOT_EDITABLE_MESSAGE = "must not be updated";
}
