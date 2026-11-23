package vn.bank.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class OtpGeneratorTest {

    private static final int SAMPLES = 1_000;

    private final OtpGenerator otpGenerator = new OtpGenerator();

    @Test
    void should_generateSixDigitOtp_when_generating() {
        // given
        var otps = new HashSet<String>();

        // when
        for (var i = 0; i < SAMPLES; i++) {
            otps.add(otpGenerator.generate());
        }

        // then: luon 6 chu so (giu so 0 o dau), khong lap lai mot gia tri co dinh
        assertThat(otps).allMatch(otp -> otp.matches("\\d{6}"));
        assertThat(otps).hasSizeGreaterThan(SAMPLES / 2);
    }
}
