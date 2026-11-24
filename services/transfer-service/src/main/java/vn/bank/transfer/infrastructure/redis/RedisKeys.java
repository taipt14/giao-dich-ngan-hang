package vn.bank.transfer.infrastructure.redis;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Key Redis cua transfer-service (muc 3.3, 12 convention; muc 6 kien-truc.md). */
public final class RedisKeys {

    private static final String IDEMPOTENCY = "idem:%s:%s";
    private static final String OTP = "otp:%s";
    private static final String OTP_FAIL = "otp:fail:%s";
    private static final String OTP_RESEND = "otp:resend:%s";
    private static final String OTP_COOLDOWN = "otp:cooldown:%s";
    private static final String DAILY_LIMIT = "limit:%s:%s";

    private RedisKeys() {}

    /**
     * Tong tien da giu cho han muc cua tai khoan nguon trong ngay nghiep vu {@code day} (dinh dang yyyyMMdd), song den
     * 00:00 ngay hom sau gio Viet Nam.
     */
    public static String dailyLimit(String accountNo, LocalDate day) {
        return DAILY_LIMIT.formatted(accountNo, day.format(DateTimeFormatter.BASIC_ISO_DATE));
    }

    /**
     * Idempotency-Key cua request tao lenh; clientId la CIF cua khach hang (cung khoa voi UK_TRANSFER_CIF_IDEM_KEY).
     * TTL theo {@code bank.transfer.idempotency.ttl}.
     */
    public static String idempotency(String cif, String idempotencyKey) {
        return IDEMPOTENCY.formatted(cif, idempotencyKey);
    }

    /** Hash cua OTP dang hieu luc, TTL {@code bank.transfer.otp.ttl}. */
    public static String otp(String transferId) {
        return OTP.formatted(transferId);
    }

    /** So lan nhap sai OTP cua lenh, TTL {@code bank.transfer.otp.ttl} tinh tu lan sai / lan gui lai gan nhat. */
    public static String otpFail(String transferId) {
        return OTP_FAIL.formatted(transferId);
    }

    /** So lan da gui lai OTP, TTL {@code bank.transfer.otp.resend-counter-ttl}. */
    public static String otpResend(String transferId) {
        return OTP_RESEND.formatted(transferId);
    }

    /** Ton tai trong luc chua duoc gui lai OTP, TTL {@code bank.transfer.otp.resend-interval} tu lan gui gan nhat. */
    public static String otpCooldown(String transferId) {
        return OTP_COOLDOWN.formatted(transferId);
    }
}
