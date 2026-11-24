package vn.bank.transfer.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import vn.bank.transfer.config.OtpProperties;

@ExtendWith(OutputCaptureExtension.class)
class OtpSenderTest {

    private static final String TRANSFER_ID = "7f1c2a9e-1d2b-4c3d-9e8f-0a1b2c3d4e5f";
    private static final String OTP = "042917";

    @Test
    void should_notLogPlainOtp_when_devLogDisabled(CapturedOutput output) {
        // given
        var sender = new OtpSender(properties(false));

        // when
        sender.send(TRANSFER_ID, OTP);

        // then
        assertThat(output).contains("OTP sent for transfer " + TRANSFER_ID).doesNotContain(OTP);
    }

    @Test
    void should_logOtpMarkedDev_when_devLogEnabled(CapturedOutput output) {
        // given
        var sender = new OtpSender(properties(true));

        // when
        sender.send(TRANSFER_ID, OTP);

        // then
        assertThat(output).contains("[DEV] OTP for transfer " + TRANSFER_ID + ": " + OTP);
    }

    private static OtpProperties properties(boolean devLogEnabled) {
        return new OtpProperties(
                Duration.ofMinutes(3), 5, 3, Duration.ofSeconds(30), Duration.ofHours(24), devLogEnabled);
    }
}
