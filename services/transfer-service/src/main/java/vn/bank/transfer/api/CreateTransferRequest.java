package vn.bank.transfer.api;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * {@code destBankCode} null → chuyen noi bo. Khong cho chuoi rong/khoang trang: Oracle luu chuoi rong thanh NULL nen
 * lenh lien ngan hang se mat ma ngan hang. {@code amount} toi da 2 chu so thap phan (cot NUMBER(19,2)).
 */
public record CreateTransferRequest(
        @NotBlank @Size(max = 20) String sourceAccount,
        @NotBlank @Size(max = 20) String destAccount,
        @Size(max = 10) @Pattern(regexp = "\\S+") String destBankCode,

        @NotNull @Positive @Digits(integer = 17, fraction = 2)
        BigDecimal amount,

        @Size(max = 210) String description) {}
