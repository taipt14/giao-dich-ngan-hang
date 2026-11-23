package vn.bank.common.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    record SamplePayload(String cif) {}

    @Test
    void should_setOccurredAtFromClock_when_createdWithOf() {
        // given
        var payload = new SamplePayload("C000000001");

        // when
        var envelope = EventEnvelope.of("CustomerCreated", "customer-service", "trace-1", payload, CLOCK);

        // then
        assertThat(envelope.occurredAt()).isEqualTo(Instant.parse("2026-11-21T10:00:00Z"));
        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.eventType()).isEqualTo("CustomerCreated");
        assertThat(envelope.source()).isEqualTo("customer-service");
        assertThat(envelope.traceId()).isEqualTo("trace-1");
        assertThat(envelope.payload()).isEqualTo(payload);
    }

    @Test
    void should_generateDifferentEventIds_when_ofCalledTwice() {
        // given
        var payload = new SamplePayload("C000000001");

        // when
        var first = EventEnvelope.of("CustomerCreated", "customer-service", null, payload, CLOCK);
        var second = EventEnvelope.of("CustomerCreated", "customer-service", null, payload, CLOCK);

        // then
        assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }
}
