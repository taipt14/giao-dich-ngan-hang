package vn.bank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AccountStatusTest {

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
        "ACTIVE, ACTIVE, false",
        "ACTIVE, LOCKED, true",
        "ACTIVE, CLOSED, true",
        "LOCKED, ACTIVE, true",
        "LOCKED, LOCKED, false",
        "LOCKED, CLOSED, true",
        "CLOSED, ACTIVE, false",
        "CLOSED, LOCKED, false",
        "CLOSED, CLOSED, false"
    })
    void should_allowOnlyDefinedTransitions_when_checkingEveryStatusPair(
            AccountStatus from, AccountStatus to, boolean allowed) {
        // given: cap trang thai (from, to) trong bang chuyen trang thai

        // when
        var result = from.canTransitionTo(to);

        // then
        assertThat(result).isEqualTo(allowed);
    }
}
