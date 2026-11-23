package vn.bank.common.outbox;

import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bat outbox cho service co dependency common-outbox: {@link OutboxWriter} de producer ghi event, job gui
 * ({@link OutboxPublisher}) va job don. Service phai co bang {@code OUTBOX_EVENT} trong migration Flyway cua minh.
 *
 * <p>Clock lay bean {@code Clock} cua service (muc 4.2 convention), khong co thi dung dong ho UTC.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    // Transaction gui batch: thoi gian gui (toi da sendTimeout) + cho Kafka xac nhan (toi da sendTimeout)
    private static final int TRANSACTION_TIMEOUT_FACTOR = 2;

    @Bean
    OutboxRepository outboxRepository(DataSource dataSource) {
        return new OutboxRepository(new JdbcTemplate(dataSource));
    }

    @Bean
    OutboxWriter outboxWriter(OutboxRepository outboxRepository) {
        return new OutboxWriter(outboxRepository);
    }

    @Bean
    OutboxSender outboxSender(ProducerFactory<?, ?> producerFactory, OutboxProperties properties) {
        return new OutboxSender(producerFactory.getConfigurationProperties(), properties.sendTimeout());
    }

    @Bean
    OutboxPublisher outboxPublisher(
            OutboxRepository outboxRepository,
            OutboxSender outboxSender,
            PlatformTransactionManager transactionManager,
            OutboxProperties properties,
            ObjectProvider<Clock> clock) {
        var transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setTimeout(
                Math.toIntExact(properties.sendTimeout().toSeconds() * TRANSACTION_TIMEOUT_FACTOR) + 1);
        return new OutboxPublisher(
                outboxRepository,
                outboxSender,
                transactionTemplate,
                properties,
                clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    OutboxPublisherJob outboxPublisherJob(OutboxPublisher outboxPublisher, OutboxProperties properties) {
        return new OutboxPublisherJob(outboxPublisher, properties);
    }

    @Bean
    OutboxCleanupJob outboxCleanupJob(
            OutboxRepository outboxRepository, OutboxProperties properties, ObjectProvider<Clock> clock) {
        return new OutboxCleanupJob(outboxRepository, properties, clock.getIfAvailable(Clock::systemUTC));
    }
}
