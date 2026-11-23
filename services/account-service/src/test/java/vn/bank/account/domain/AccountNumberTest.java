package vn.bank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;

class AccountNumberTest {

    @Test
    void should_prefix1000AndPadTo12Digits_when_sequenceIsSmall() {
        // given
        var sequenceValue = 7L;

        // when
        var accountNo = AccountNumber.fromSequence(sequenceValue);

        // then
        assertThat(accountNo).isEqualTo("100000000007");
    }

    @Test
    void should_use8DigitsOfSequence_when_sequenceAtMaximum() {
        // given
        var sequenceValue = 99_999_999L;

        // when
        var accountNo = AccountNumber.fromSequence(sequenceValue);

        // then
        assertThat(accountNo).isEqualTo("100099999999").hasSize(12);
    }

    @Test
    void should_reject_when_sequenceOutOfRange() {
        // given
        var tooLarge = 100_000_000L;

        // when
        var thrownTooLarge = catchThrowable(() -> AccountNumber.fromSequence(tooLarge));
        var thrownZero = catchThrowable(() -> AccountNumber.fromSequence(0));

        // then
        assertThat(thrownTooLarge).isInstanceOf(IllegalArgumentException.class);
        assertThat(thrownZero).isInstanceOf(IllegalArgumentException.class);
    }
}
