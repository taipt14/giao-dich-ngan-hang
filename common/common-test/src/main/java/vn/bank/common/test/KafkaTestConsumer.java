package vn.bank.common.test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Doc message tu mot topic Kafka trong integration test, co timeout.
 *
 * <p>Moi instance dung consumer group rieng va doc tu dau topic ({@code earliest}) nen thay ca message gui truoc khi
 * tao consumer. Kafka container dung chung giua cac test nen topic co the chua message cua test khac: luon loc bang
 * dieu kien (vi du theo key). Key va value doc dang {@code String} (JSON tho), test tu parse neu can.
 */
public final class KafkaTestConsumer implements AutoCloseable {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);
    private static final String GROUP_ID_PREFIX = "kafka-test-consumer-";
    private static final String OFFSET_RESET_EARLIEST = "earliest";

    private final KafkaConsumer<String, String> consumer;
    private final String topic;

    private KafkaTestConsumer(KafkaConsumer<String, String> consumer, String topic) {
        this.consumer = consumer;
        this.topic = topic;
    }

    public static KafkaTestConsumer subscribe(String bootstrapServers, String topic) {
        var properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID_PREFIX + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, OFFSET_RESET_EARLIEST);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        var consumer = new KafkaConsumer<String, String>(properties);
        consumer.subscribe(List.of(topic));
        return new KafkaTestConsumer(consumer, topic);
    }

    /** Cho toi da {@code timeout} message dau tien thoa {@code matcher}; het thoi gian thi tra rong. */
    public Optional<ConsumerRecord<String, String>> findRecord(
            Predicate<ConsumerRecord<String, String>> matcher, Duration timeout) {
        // nanoTime chi dung do khoang thoi gian cho, khong phai thoi diem nghiep vu nen khong can Clock
        var deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            for (var consumerRecord : consumer.poll(POLL_INTERVAL)) {
                if (matcher.test(consumerRecord)) {
                    return Optional.of(consumerRecord);
                }
            }
        }
        return Optional.empty();
    }

    /** Nhu {@link #findRecord} nhung bao {@link AssertionError} neu khong co message thoa dieu kien trong timeout. */
    public ConsumerRecord<String, String> getRecord(
            Predicate<ConsumerRecord<String, String>> matcher, Duration timeout) {
        return findRecord(matcher, timeout)
                .orElseThrow(() -> new AssertionError("No matching record on topic " + topic + " within " + timeout));
    }

    /** Cho message co key bang {@code key}; khong co trong timeout thi bao {@link AssertionError}. */
    public ConsumerRecord<String, String> getRecordByKey(String key, Duration timeout) {
        return getRecord(consumerRecord -> key.equals(consumerRecord.key()), timeout);
    }

    @Override
    public void close() {
        consumer.close();
    }
}
