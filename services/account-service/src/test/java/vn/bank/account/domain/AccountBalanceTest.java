package vn.bank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountBalanceTest {

    private static final String CIF = "C000000001";

    @Test
    void should_beOwned_when_cifMatchesCustomer() {
        // given
        var balance = new AccountBalance("100000000007", CIF, "VND", BigDecimal.ZERO, BigDecimal.ZERO);

        // when
        var owned = balance.isOwnedBy(CIF);

        // then
        assertThat(owned).isTrue();
    }

    @Test
    void should_notBeOwnedByAnyCustomer_when_glAccountHasNoCif() {
        // given
        var balance = new AccountBalance("CASH_VAULT", null, "VND", new BigDecimal("-100.00"), BigDecimal.ZERO);

        // when
        var owned = balance.isOwnedBy(CIF);

        // then
        assertThat(owned).isFalse();
    }
}
