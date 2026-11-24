package vn.bank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OtpHashTest {

    private static final String TRANSFER_ID = "7f1c2a9e-1d2b-4c3d-9e8f-0a1b2c3d4e5f";
    private static final String OTHER_TRANSFER_ID = "0a1b2c3d-1d2b-4c3d-9e8f-7f1c2a9e4e5f";
    private static final String OTP = "042917";

    @Test
    void should_returnSha256HexWithoutPlainOtp_when_hashing() {
        // when
        var hash = OtpHash.of(TRANSFER_ID, OTP);

        // then
        assertThat(hash).hasSize(64).matches("[0-9a-f]+").doesNotContain(OTP);
        assertThat(OtpHash.of(TRANSFER_ID, OTP)).isEqualTo(hash);
    }

    @Test
    void should_returnDifferentHash_when_sameOtpOnOtherTransfer() {
        // when
        var hash = OtpHash.of(TRANSFER_ID, OTP);
        var otherHash = OtpHash.of(OTHER_TRANSFER_ID, OTP);

        // then: transferId la salt
        assertThat(otherHash).isNotEqualTo(hash);
    }

    @Test
    void should_returnDifferentHash_when_otpDiffers() {
        // when
        var hash = OtpHash.of(TRANSFER_ID, OTP);
        var otherHash = OtpHash.of(TRANSFER_ID, "042918");

        // then
        assertThat(otherHash).isNotEqualTo(hash);
    }
}
