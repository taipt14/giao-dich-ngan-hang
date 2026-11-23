package vn.bank.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;

class CifTest {

    @Test
    void should_formatCPlusNineDigits_when_createdFromSequence() {
        // when
        var cif = Cif.fromSequence(42L);

        // then
        assertThat(cif).isEqualTo("C000000042");
    }

    @Test
    void should_formatMaxCif_when_sequenceAtMaxValue() {
        // when
        var cif = Cif.fromSequence(999_999_999L);

        // then
        assertThat(cif).isEqualTo("C999999999");
    }

    @Test
    void should_reject_when_sequenceExceedsNineDigits() {
        // when
        var thrown = catchThrowable(() -> Cif.fromSequence(1_000_000_000L));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }
}
