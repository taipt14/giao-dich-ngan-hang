package vn.bank.common.events;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.converter.RecordMessageConverter;

/**
 * Cau hinh Kafka dung chung cho service co dependency common-events: value producer la JSON qua {@link EventJson},
 * consumer chuyen JSON thanh kieu tham so {@code @KafkaListener}, producer mac dinh {@code acks=all} va
 * {@code enable.idempotence=true}.
 *
 * <p>Key va value phia consumer giu mac dinh {@code StringDeserializer} cua Spring Boot.
 */
@AutoConfiguration(before = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
public class KafkaEventsAutoConfiguration {

    /** Chi ap dung khi service chua tu dat qua {@code spring.kafka.producer.*}. */
    static final Map<String, Object> PRODUCER_DEFAULTS =
            Map.of(ProducerConfig.ACKS_CONFIG, "all", ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

    @Bean
    DefaultKafkaProducerFactoryCustomizer eventProducerFactoryCustomizer() {
        return KafkaEventsAutoConfiguration::customizeProducerFactory;
    }

    @Bean
    @ConditionalOnMissingBean
    RecordMessageConverter eventMessageConverter() {
        return EventJson.messageConverter();
    }

    // Spring Boot khai bao producer factory dang DefaultKafkaProducerFactory<?, ?>, value cua project luon la Object
    @SuppressWarnings("unchecked")
    private static void customizeProducerFactory(DefaultKafkaProducerFactory<?, ?> factory) {
        ((DefaultKafkaProducerFactory<Object, Object>) factory).setValueSerializer(EventJson.serializer());

        var current = factory.getConfigurationProperties();
        var defaults = new HashMap<String, Object>();
        PRODUCER_DEFAULTS.forEach((key, value) -> {
            if (!current.containsKey(key)) {
                defaults.put(key, value);
            }
        });
        factory.updateConfigs(defaults);
    }
}
