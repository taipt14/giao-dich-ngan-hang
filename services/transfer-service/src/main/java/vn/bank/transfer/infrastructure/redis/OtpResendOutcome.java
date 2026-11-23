package vn.bank.transfer.infrastructure.redis;

public enum OtpResendOutcome {
    RESENT,
    // Chua qua bank.transfer.otp.resend-interval tu lan gui truoc
    TOO_SOON,
    // Da gui lai du bank.transfer.otp.max-resend lan
    LIMIT_EXCEEDED
}
