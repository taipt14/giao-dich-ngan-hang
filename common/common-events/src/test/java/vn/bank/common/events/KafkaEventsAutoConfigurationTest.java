package vn.bank.common.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJacksonJsonMessageConverter;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

class KafkaEventsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaEventsAutoConfiguration.class, KafkaAutoConfiguration.class));

    @Test
    void should_useAcksAllAndIdempotence_when_producerNotConfigured() {
        runner.run(context -> {
            // when
            var configs = context.getBean(DefaultKafkaProducerFactory.class).getConfigurationProperties();

            // then
            assertThat(configs)
                    .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                    .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        });
    }

    @Test
    void should_keepServiceValue_when_producerAcksConfigured() {
        runner.withPropertyValues("spring.kafka.producer.acks=1").run(context -> {
            // when
            var configs = context.getBean(DefaultKafkaProducerFactory.class).getConfigurationProperties();

            // then
            assertThat(configs)
                    .containsEntry(ProducerConfig.ACKS_CONFIG, "1")
                    .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        });
    }

    @Test
    void should_useEventJsonSerializer_when_producerFactoryCreated() {
        runner.run(context -> {
            // when
            var factory = context.getBean(DefaultKafkaProducerFactory.class);

            // then
            assertThat(factory.getValueSerializer()).isInstanceOf(JacksonJsonSerializer.class);
            assertThat(((JacksonJsonSerializer<?>) factory.getValueSerializer()).isAddTypeInfo())
                    .isFalse();
        });
    }

    @Test
    void should_registerJsonMessageConverter_when_noConverterDefined() {
        runner.run(context -> assertThat(context.getBean(RecordMessageConverter.class))
                .isInstanceOf(StringJacksonJsonMessageConverter.class));
    }

    @Test
    void should_backOff_when_serviceDefinesOwnConverter() {
        // given
        var ownConverter = new StringJacksonJsonMessageConverter();

        // when / then
        runner.withBean(RecordMessageConverter.class, () -> ownConverter)
                .run(context -> assertThat(context.getBean(RecordMessageConverter.class))
                        .isSameAs(ownConverter));
    }
}
