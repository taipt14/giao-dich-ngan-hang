package vn.bank.transfer.application;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Sinh OTP 6 chu so bang {@link SecureRandom} (khong doan truoc duoc nhu {@code Random}), giu so 0 o dau. */
@Component
public class OtpGenerator {

    private static final int OTP_BOUND = 1_000_000;
    private static final String OTP_FORMAT = "%06d";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return OTP_FORMAT.formatted(random.nextInt(OTP_BOUND));
    }
}
