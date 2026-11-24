package vn.bank.common.outbox;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Gui value JSON da luu trong outbox len Kafka nguyen van (serializer String). Khong dung {@code KafkaTemplate} cua
 * service vi value serializer cua no la JSON: gui String se bi boc them dau nhay.
 *
 * <p>Dung lai cau hinh producer cua service (bootstrap, {@code acks=all}, idempotence) va gioi han thoi gian gui theo
 * {@code sendTimeout}: Kafka khong san sang thi message het han trong producer, khong bi gui muon sau khi outbox da
 * tinh la mot lan loi. Khong khai bao la bean {@code KafkaTemplate}/{@code ProducerFactory} de khong lam Spring Boot
 * bo qua bean mac dinh cua no.
 */
class OutboxSender implements DisposableBean {

    private final DefaultKafkaProducerFactory<String, String> producerFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;

    OutboxSender(Map<String, Object> producerConfigs, Duration sendTimeout) {
        var timeoutMillis = Math.toIntExact(sendTimeout.toMillis());
        var configs = new HashMap<>(producerConfigs);
        // delivery.timeout.ms phai >= linger.ms + request.timeout.ms
        configs.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, timeoutMillis);
        configs.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, timeoutMillis / 2);
        configs.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, (long) timeoutMillis / 2);
        this.producerFactory =
                new DefaultKafkaProducerFactory<>(configs, new StringSerializer(), new StringSerializer());
        this.kafkaTemplate = new KafkaTemplate<>(producerFactory);
    }

    CompletableFuture<SendResult<String, String>> send(String topic, String key, String value) {
        return kafkaTemplate.send(topic, key, value);
    }

    @Override
    public void destroy() {
        producerFactory.destroy();
    }
}
