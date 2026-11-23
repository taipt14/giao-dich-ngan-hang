package vn.bank.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");
    private static final int MAX_ATTEMPTS = 10;
    private static final int BATCH_SIZE = 100;

    private final OutboxRepository repository = mock(OutboxRepository.class);
    private final OutboxSender sender = mock(OutboxSender.class);
    private final OutboxPublisher publisher = new OutboxPublisher(
            repository,
            sender,
            new TransactionTemplate(mock(PlatformTransactionManager.class)),
            new OutboxProperties(BATCH_SIZE, MAX_ATTEMPTS, Duration.ofSeconds(1), Duration.ofDays(7)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void should_sendPayloadWithAggregateKeyAndMarkSent_when_kafkaAcknowledges() {
        // given
        var event = eventWithRetryCount(0);
        givenBatch(event);
        when(sender.send(anyString(), anyString(), anyString())).thenReturn(acknowledged());

        // when
        var sent = publisher.publishBatch();

        // then
        assertThat(sent).isEqualTo(1);
        verify(sender).send("account.events", "100012345678", "{\"eventId\":\"x\"}");
        verify(repository).markSent(event.eventId(), NOW);
        verify(repository, never()).markAttemptFailed(any(), anyInt(), any());
    }

    @Test
    void should_keepNewAndIncreaseRetryCount_when_sendFails() {
        // given
        var event = eventWithRetryCount(3);
        givenBatch(event);
        when(sender.send(anyString(), anyString(), anyString())).thenReturn(failed());

        // when
        var sent = publisher.publishBatch();

        // then
        assertThat(sent).isZero();
        verify(repository).markAttemptFailed(event.eventId(), 4, OutboxStatus.NEW);
        verify(repository, never()).markSent(any(), any());
    }

    @Test
    void should_markFailed_when_sendFailsForTenthTime() {
        // given
        var event = eventWithRetryCount(MAX_ATTEMPTS - 1);
        givenBatch(event);
        when(sender.send(anyString(), anyString(), anyString())).thenReturn(failed());

        // when
        publisher.publishBatch();

        // then
        verify(repository).markAttemptFailed(event.eventId(), MAX_ATTEMPTS, OutboxStatus.FAILED);
    }

    @Test
    void should_countFailureAsAttempt_when_kafkaDoesNotAcknowledgeWithinSendTimeout() {
        // given
        var event = eventWithRetryCount(0);
        givenBatch(event);
        when(sender.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());

        // when
        publisher.publishBatch();

        // then
        verify(repository).markAttemptFailed(event.eventId(), 1, OutboxStatus.NEW);
    }

    @Test
    void should_stopBatchWithoutCountingRest_when_sendFailsImmediately() {
        // given: Kafka chet, lan gui dau loi ngay (khong lay duoc metadata)
        var first = eventWithRetryCount(0);
        var second = eventWithRetryCount(0);
        givenBatch(first, second);
        when(sender.send(anyString(), anyString(), anyString())).thenReturn(failed());

        // when
        publisher.publishBatch();

        // then
        verify(sender, times(1)).send(anyString(), anyString(), anyString());
        verify(repository).markAttemptFailed(first.eventId(), 1, OutboxStatus.NEW);
        verify(repository, never()).markAttemptFailed(eq(second.eventId()), anyInt(), any());
    }

    @Test
    void should_sendNothing_when_noNewEvent() {
        // given
        when(repository.lockNextBatch(BATCH_SIZE)).thenReturn(List.of());

        // when
        var sent = publisher.publishBatch();

        // then
        assertThat(sent).isZero();
        verify(sender, never()).send(anyString(), anyString(), anyString());
    }

    private void givenBatch(OutboxEvent... events) {
        when(repository.lockNextBatch(BATCH_SIZE)).thenReturn(List.of(events));
    }

    private static OutboxEvent eventWithRetryCount(int retryCount) {
        return new OutboxEvent(
                UUID.randomUUID(),
                "ACCOUNT",
                "100012345678",
                "account.events",
                "BalanceChanged",
                "{\"eventId\":\"x\"}",
                retryCount,
                NOW);
    }

    private static CompletableFuture<SendResult<String, String>> acknowledged() {
        return CompletableFuture.completedFuture(new SendResult<>(null, null));
    }

    private static CompletableFuture<SendResult<String, String>> failed() {
        return CompletableFuture.failedFuture(new TimeoutException("Topic account.events not present in metadata"));
    }
}
