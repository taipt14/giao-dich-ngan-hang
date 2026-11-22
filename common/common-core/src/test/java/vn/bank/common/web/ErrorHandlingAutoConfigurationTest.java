package vn.bank.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

class ErrorHandlingAutoConfigurationTest {

    private static final AutoConfigurations AUTO_CONFIG = AutoConfigurations.of(ErrorHandlingAutoConfiguration.class);

    @Test
    void should_registerGlobalExceptionHandler_when_servletWebApplication() {
        // given
        var runner = new WebApplicationContextRunner().withConfiguration(AUTO_CONFIG);

        // when / then
        runner.run(context -> assertThat(context).hasSingleBean(GlobalExceptionHandler.class));
    }

    @Test
    void should_notRegisterGlobalExceptionHandler_when_notWebApplication() {
        // given
        var runner = new ApplicationContextRunner().withConfiguration(AUTO_CONFIG);

        // when / then
        runner.run(context -> assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class));
    }

    @Test
    void should_useServiceClock_when_clockBeanDefined() {
        // given
        var serviceClock = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC);
        var runner = new WebApplicationContextRunner()
                .withConfiguration(AUTO_CONFIG)
                .withBean(Clock.class, () -> serviceClock);

        // when / then
        runner.run(context -> assertThat(
                        ReflectionTestUtils.getField(context.getBean(GlobalExceptionHandler.class), "clock"))
                .isSameAs(serviceClock));
    }

    @Test
    void should_backOff_when_serviceDefinesOwnHandler() {
        // given
        var ownHandler = new GlobalExceptionHandler(Clock.systemUTC());
        var runner = new WebApplicationContextRunner()
                .withConfiguration(AUTO_CONFIG)
                .withBean(GlobalExceptionHandler.class, () -> ownHandler);

        // when / then
        runner.run(context -> {
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            assertThat(context.getBean(GlobalExceptionHandler.class)).isSameAs(ownHandler);
        });
    }
}
