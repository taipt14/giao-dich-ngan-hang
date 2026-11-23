package vn.bank.transfer.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** OTP 6 chu so khach hang nhan duoc. Sai dinh dang tra 400, khong tinh la mot lan nhap sai OTP. */
public record ConfirmOtpRequest(
        @NotNull @Pattern(regexp = "\\d{6}") String otp) {

    // Khong de OTP lot vao log khi record bi in ra (muc 13 convention)
    @Override
    public String toString() {
        return "ConfirmOtpRequest[otp=******]";
    }
}
