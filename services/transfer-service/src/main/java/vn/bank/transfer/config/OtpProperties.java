package vn.bank.transfer.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * OTP xac thuc lenh chuyen khoan (BANK-042).
 *
 * @param ttl thoi gian hieu luc cua mot OTP, cung la TTL cua bo dem sai {@code otp:fail:{transferId}}
 * @param maxAttempts so lan nhap sai OTP thi lenh FAILED ({@code OTP_MAX_ATTEMPTS})
 * @param maxResend so lan toi da duoc gui lai OTP (khong tinh lan gui dau khi tao lenh)
 * @param resendInterval khoang cach toi thieu giua hai lan gui OTP
 * @param resendCounterTtl thoi gian giu bo dem so lan gui lai; phai dai hon thoi gian lenh cho OTP
 * @param devLogEnabled chi bat o moi truong dev: ghi OTP dang ro ra log thay cho gui SMS
 */
@Validated
@ConfigurationProperties(prefix = "bank.transfer.otp")
public record OtpProperties(
        @NotNull Duration ttl,
        @Min(1) int maxAttempts,
        @Min(0) int maxResend,
        @NotNull Duration resendInterval,
        @NotNull Duration resendCounterTtl,
        boolean devLogEnabled) {}
