package vn.bank.transfer.infrastructure.redis;

/**
 * Ket qua so OTP voi hash dang luu. {@code failedAttempts}: so lan sai tinh ca lan nay (co nghia voi
 * {@link Outcome#MISMATCH} va {@link Outcome#MAX_ATTEMPTS_REACHED}).
 */
public record OtpVerification(Outcome outcome, int failedAttempts) {

    public enum Outcome {
        // Dung OTP: OTP da bi xoa, khong dung lai duoc
        VERIFIED,
        // Khong con OTP: het han hoac chua tung luu
        EXPIRED,
        MISMATCH,
        // Lan sai nay hoac truoc do da cham so lan toi da
        MAX_ATTEMPTS_REACHED
    }
}
