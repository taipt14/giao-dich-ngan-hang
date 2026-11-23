package vn.bank.account.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;

class ReconcilePropertiesTest {

    @Test
    void should_acceptCron_when_expressionValid() {
        // when
        var properties = new ReconcileProperties("0 55 23 * * *");

        // then
        assertThat(properties.cron()).isEqualTo("0 55 23 * * *");
    }

    @Test
    void should_acceptDisabledMarker_when_cronIsDash() {
        // when
        var properties = new ReconcileProperties("-");

        // then
        assertThat(properties.cron()).isEqualTo("-");
    }

    @Test
    void should_rejectConfiguration_when_cronInvalid() {
        // when
        var thrown = catchThrowable(() -> new ReconcileProperties("55 23 * *"));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bank.account.reconcile");
    }
}
