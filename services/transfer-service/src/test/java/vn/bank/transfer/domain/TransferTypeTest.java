package vn.bank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TransferTypeTest {

    @ParameterizedTest(name = "{0} supports {1}: {2}")
    @CsvSource({
        "INTERNAL, INITIATED, true",
        "INTERNAL, OTP_PENDING, true",
        "INTERNAL, PROCESSING, true",
        "INTERNAL, PENDING_NAPAS, false",
        "INTERNAL, SUCCESS, true",
        "INTERNAL, FAILED, true",
        "INTERNAL, REVERSED, false",
        "INTERNAL, UNKNOWN, true",
        "INTERBANK, INITIATED, true",
        "INTERBANK, OTP_PENDING, true",
        "INTERBANK, PROCESSING, true",
        "INTERBANK, PENDING_NAPAS, true",
        "INTERBANK, SUCCESS, true",
        "INTERBANK, FAILED, true",
        "INTERBANK, REVERSED, true",
        "INTERBANK, UNKNOWN, true"
    })
    void should_supportNapasStatusesOnlyForInterbank_when_checkingEveryStatus(
            TransferType type, TransferStatus status, boolean supported) {
        // given: loai lenh va trang thai

        // when
        var result = type.supports(status);

        // then
        assertThat(result).isEqualTo(supported);
    }
}
