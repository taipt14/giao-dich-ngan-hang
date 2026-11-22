package vn.bank.common.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

class EventEnvelopeJsonTest {

    private static final String TOPIC = Topics.TRANSFER_EVENTS;
    private static final UUID EVENT_ID = UUID.fromString("7d3f1c2e-8a4b-4c1d-9e2f-0a1b2c3d4e5f");
    private static final Instant OCCURRED_AT = Instant.parse("2026-11-21T10:00:00Z");
    private static final BigDecimal AMOUNT = new BigDecimal("1234567.89");

    record SamplePayload(String transferId, BigDecimal amount) {
    }

    @Test
    void should_keepBigDecimalValue_when_serializeThenDeserializeEnvelope() {
        // given
        var envelope = envelopeOf(new SamplePayload("TRF-1", AMOUNT));

        // when
        var json = serialize(envelope);
        var restored = deserialize(json, typeOf(SamplePayload.class));

        // then
        assertThat(json).contains("\"amount\":1234567.89").contains("\"occurredAt\":\"2026-11-21T10:00:00Z\"");
        assertThat(restored).isEqualTo(envelope);
        assertThat(restored.payload()).isInstanceOf(SamplePayload.class);
        assertThat(((SamplePayload) restored.payload()).amount()).isEqualByComparingTo(AMOUNT);
    }

    @Test
    void should_keepBigDecimalValue_when_payloadDeserializedWithoutDeclaredType() {
        // given
        var envelope = envelopeOf(new SamplePayload("TRF-1", AMOUNT));

        // when
        var restored = deserialize(serialize(envelope), typeOf(Map.class));

        // then
        assertThat(((Map<?, ?>) restored.payload()).get("amount")).isEqualTo(AMOUNT);
    }

    @Test
    void should_failWithClearMessage_when_deserializeEnvelopeMissingEventId() {
        // given
        var json = """
                {"eventType":"TransferCompleted","occurredAt":"2026-11-21T10:00:00Z","traceId":"abc",
                 "source":"transfer-service","payload":{"transferId":"TRF-1","amount":1234567.89}}
                """;

        // when
        var thrown = catchThrowable(() -> deserialize(json, typeOf(SamplePayload.class)));

        // then
        assertThat(thrown).isNotNull().rootCause().hasMessage("EventEnvelope.eventId is required");
    }

    @Test
    void should_rejectEnvelope_when_createdWithoutEventId() {
        // when
        var thrown = catchThrowable(() -> new EventEnvelope<>(
                null, "TransferCompleted", OCCURRED_AT, "abc", "transfer-service", new SamplePayload("TRF-1", AMOUNT)));

        // then
        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EventEnvelope.eventId is required");
    }

    @Test
    void should_acceptEnvelope_when_traceIdMissing() {
        // given
        var envelope = new EventEnvelope<>(
                EVENT_ID, "TransferCompleted", OCCURRED_AT, null, "transfer-service", new SamplePayload("TRF-1", AMOUNT));

        // when
        var restored = deserialize(serialize(envelope), typeOf(SamplePayload.class));

        // then
        assertThat(restored.traceId()).isNull();
    }

    private static EventEnvelope<SamplePayload> envelopeOf(SamplePayload payload) {
        return new EventEnvelope<>(EVENT_ID, "TransferCompleted", OCCURRED_AT, "abc", "transfer-service", payload);
    }

    private static String serialize(EventEnvelope<?> envelope) {
        try (var serializer = EventJson.serializer()) {
            return new String(serializer.serialize(TOPIC, envelope), StandardCharsets.UTF_8);
        }
    }

    // Di qua converter giong luong @KafkaListener: value String + kieu tham so method
    private static EventEnvelope<?> deserialize(String json, Type envelopeType) {
        var record = new ConsumerRecord<>(TOPIC, 0, 0L, "TRF-1", json);
        var message = EventJson.messageConverter().toMessage(record, null, null, envelopeType);
        return (EventEnvelope<?>) message.getPayload();
    }

    private static Type typeOf(Class<?> payloadType) {
        return ResolvableType.forClassWithGenerics(EventEnvelope.class, payloadType).getType();
    }
}
