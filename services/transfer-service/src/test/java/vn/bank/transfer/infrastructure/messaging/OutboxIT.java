package vn.bank.transfer.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.bank.common.events.EventEnvelope;
import vn.bank.common.events.EventJson;
import vn.bank.common.outbox.OutboxCleanupJob;
import vn.bank.common.outbox.OutboxPublisher;
import vn.bank.common.outbox.OutboxWriter;
import vn.bank.common.test.AbstractIntegrationTest;

/**
 * Outbox (BANK-046) voi Oracle, Kafka that, tren schema TRANSFER_SVC. Event test gui len topic rieng de khong lan voi
 * event nghiep vu. Moi context Spring dang cache trong JVM test cung chay publisher tren cung bang, nen moi test chi
 * kiem tra event cua chinh no va coi cac publisher do nhu instance khac.
 */
@SpringBootTest
class OutboxIT extends AbstractIntegrationTest {

    private static final String TEST_TOPIC = "transfer.outbox-it";
    private static final String TEST_EVENT_TYPE = "OutboxItTested";
    private static final String AGGREGATE_TYPE = "OUTBOX_IT";
    private static final int EVENT_COUNT = 30;
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(60);
    // Nhieu lan chu ky publisher (1 giay): neu event da commit thi chac chan da duoc gui trong khoang nay
    private static final Duration NO_EVENT_WAIT = Duration.ofSeconds(4);
    // Doc het message cua topic test (tu dau topic) trong khoang nay de dem trung
    private static final Duration COLLECT_WINDOW = Duration.ofSeconds(5);

    private final OutboxWriter outboxWriter;
    private final OutboxPublisher outboxPublisher;
    private final OutboxCleanupJob outboxCleanupJob;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    OutboxIT(
            @Autowired OutboxWriter outboxWriter,
            @Autowired OutboxPublisher outboxPublisher,
            @Autowired OutboxCleanupJob outboxCleanupJob,
            @Autowired JdbcTemplate jdbcTemplate,
            @Autowired PlatformTransactionManager transactionManager,
            @Autowired Clock clock) {
        this.outboxWriter = outboxWriter;
        this.outboxPublisher = outboxPublisher;
        this.outboxCleanupJob = outboxCleanupJob;
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Test
    void should_notPublishEvent_when_transactionRolledBack() {
        // given
        var aggregateId = UUID.randomUUID().toString();
        var event = testEvent(0);

        try (var consumer = createKafkaTestConsumer(TEST_TOPIC)) {
            // when
            transactionTemplate.executeWithoutResult(status -> {
                outboxWriter.write(AGGREGATE_TYPE, aggregateId, TEST_TOPIC, event);
                status.setRollbackOnly();
            });

            // then
            assertThat(countRows(List.of(event.eventId().toString()), null)).isZero();
            assertThat(consumer.findRecord(consumerRecord -> aggregateId.equals(consumerRecord.key()), NO_EVENT_WAIT))
                    .isEmpty();
        }
    }

    @Test
    void should_publishEachEventExactlyOnce_when_twoPublisherInstancesRunConcurrently() throws Exception {
        // given
        var eventIds = writeEventsInOneTransaction();

        // when: 2 instance (2 thread, moi thread transaction va connection rieng) cung quet mot luc
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = CompletableFuture.runAsync(() -> publishUntilAllSent(eventIds, start), executor);
            var second = CompletableFuture.runAsync(() -> publishUntilAllSent(eventIds, start), executor);
            start.countDown();
            CompletableFuture.allOf(first, second).get(SEND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        }

        // then
        assertThat(countRows(eventIds, "SENT")).isEqualTo(EVENT_COUNT);
        assertThat(receivedEventIds(eventIds)).containsExactlyInAnyOrderElementsOf(eventIds);
    }

    @Test
    void should_deleteOnlySentEventsOlderThanSevenDays_when_cleanupRuns() {
        // given
        var now = clock.instant();
        var oldSent = insertRow("SENT", now.minus(Duration.ofDays(30)), now.minus(Duration.ofDays(8)));
        var recentSent = insertRow("SENT", now.minus(Duration.ofDays(30)), now.minus(Duration.ofDays(6)));
        var oldFailed = insertRow("FAILED", now.minus(Duration.ofDays(30)), null);

        // when
        var deleted = outboxCleanupJob.cleanup();

        // then
        assertThat(deleted).isPositive();
        assertThat(countRows(List.of(oldSent), null)).isZero();
        assertThat(countRows(List.of(recentSent, oldFailed), null)).isEqualTo(2);
    }

    private List<String> writeEventsInOneTransaction() {
        var eventIds = new ArrayList<String>();
        transactionTemplate.executeWithoutResult(status -> {
            for (var sequence = 0; sequence < EVENT_COUNT; sequence++) {
                var event = testEvent(sequence);
                outboxWriter.write(AGGREGATE_TYPE, UUID.randomUUID().toString(), TEST_TOPIC, event);
                eventIds.add(event.eventId().toString());
            }
        });
        return eventIds;
    }

    private void publishUntilAllSent(List<String> eventIds, CountDownLatch start) {
        awaitStart(start);
        await().atMost(SEND_TIMEOUT).pollDelay(Duration.ZERO).until(() -> {
            outboxPublisher.publishBatch();
            return countRows(eventIds, "NEW") == 0;
        });
    }

    // Khong co message nao bi mat hay bi gui 2 lan: moi eventId xuat hien dung mot lan tren topic
    private List<String> receivedEventIds(List<String> eventIds) {
        var received = new ArrayList<String>();
        try (var consumer = createKafkaTestConsumer(TEST_TOPIC)) {
            // Matcher luon tra false de doc het topic trong COLLECT_WINDOW, chi gom message cua test nay
            consumer.findRecord(
                    consumerRecord -> {
                        var eventId = EventJson.mapper()
                                .readTree(consumerRecord.value())
                                .get("eventId")
                                .asString();
                        if (eventIds.contains(eventId)) {
                            received.add(eventId);
                        }
                        return false;
                    },
                    COLLECT_WINDOW);
        }
        return received;
    }

    private int countRows(List<String> eventIds, String status) {
        var sql = "SELECT COUNT(*) FROM OUTBOX_EVENT WHERE EVENT_ID IN (:ids)"
                + (status == null ? "" : " AND STATUS = :status");
        var params = new MapSqlParameterSource("ids", eventIds).addValue("status", status);
        return namedJdbcTemplate.queryForObject(sql, params, Integer.class);
    }

    private String insertRow(String status, Instant createdAt, Instant sentAt) {
        var eventId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                """
                INSERT INTO OUTBOX_EVENT (EVENT_ID, AGGREGATE_TYPE, AGGREGATE_ID, TOPIC, EVENT_TYPE, PAYLOAD, STATUS,
                    RETRY_COUNT, CREATED_AT, SENT_AT)
                VALUES (?, ?, ?, ?, ?, '{}', ?, 0, ?, ?)""",
                eventId,
                AGGREGATE_TYPE,
                eventId,
                TEST_TOPIC,
                TEST_EVENT_TYPE,
                status,
                LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC),
                new SqlParameterValue(
                        Types.TIMESTAMP, sentAt == null ? null : LocalDateTime.ofInstant(sentAt, ZoneOffset.UTC)));
        return eventId;
    }

    private EventEnvelope<Map<String, Integer>> testEvent(int sequence) {
        return EventEnvelope.of(TEST_EVENT_TYPE, "transfer-service", null, Map.of("sequence", sequence), clock);
    }

    private static void awaitStart(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted before publishing", e);
        }
    }
}
