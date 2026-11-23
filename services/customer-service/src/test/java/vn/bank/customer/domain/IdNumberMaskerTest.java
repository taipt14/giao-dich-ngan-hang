package vn.bank.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdNumberMaskerTest {

    @Test
    void should_keepFirstFourAndLastTwoDigits_when_maskingIdNumber() {
        // when
        var masked = IdNumberMasker.mask("001234567889");

        // then
        assertThat(masked).isEqualTo("0012******89");
    }

    @Test
    void should_maskAllCharacters_when_valueTooShort() {
        // when
        var masked = IdNumberMasker.mask("12345");

        // then
        assertThat(masked).isEqualTo("*****");
    }
}
