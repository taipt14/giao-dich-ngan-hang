package vn.bank.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.bank.common.events.EventEnvelope;
import vn.bank.common.events.EventJson;

class OutboxWriterTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final OutboxRepository repository = mock(OutboxRepository.class);
    private final OutboxWriter writer = new OutboxWriter(repository);

    @AfterEach
    void endTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void should_storeWholeEnvelopeAsJsonWithAggregateKey_when_insideTransaction() {
        // given
        TransactionSynchronizationManager.setActualTransactionActive(true);
        var event = EventEnvelope.of(
                "BalanceChanged", "account-service", "trace-1", Map.of("amount", new BigDecimal("1000000.00")), CLOCK);

        // when
        writer.write("ACCOUNT", "100012345678", "account.events", event);

        // then
        var captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).insert(captor.capture());
        var stored = captor.getValue();
        assertThat(stored.eventId()).isEqualTo(event.eventId());
        assertThat(stored.aggregateType()).isEqualTo("ACCOUNT");
        assertThat(stored.aggregateId()).isEqualTo("100012345678");
        assertThat(stored.topic()).isEqualTo("account.events");
        assertThat(stored.eventType()).isEqualTo("BalanceChanged");
        assertThat(stored.retryCount()).isZero();
        assertThat(stored.createdAt()).isEqualTo(NOW);
        var json = EventJson.mapper().readTree(stored.payload());
        assertThat(json.get("eventId").asString()).isEqualTo(event.eventId().toString());
        assertThat(json.get("occurredAt").asString()).isEqualTo("2026-11-23T03:00:00Z");
        assertThat(json.get("traceId").asString()).isEqualTo("trace-1");
        assertThat(stored.payload()).contains("\"amount\":1000000.00");
    }

    @Test
    void should_rejectWrite_when_noTransactionActive() {
        // given
        var event = EventEnvelope.of("BalanceChanged", "account-service", null, Map.of("k", "v"), CLOCK);

        // when
        var thrown = catchThrowable(() -> writer.write("ACCOUNT", "100012345678", "account.events", event));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class).hasMessageContaining("inside a transaction");
        verify(repository, never()).insert(any());
    }
}
