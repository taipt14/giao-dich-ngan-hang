package vn.bank.common.events;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Vo boc chung cho moi event Kafka (muc 5 docs/kien-truc.md). Thieu truong bat buoc thi nem
 * {@link IllegalArgumentException} ngay khi tao, ke ca khi Jackson deserialize, de message loi chi ro truong nao thieu.
 *
 * <p>{@code traceId} duoc phep null: service chua bat tracing (BANK-070) van gui duoc event.
 */
public record EventEnvelope<T>(
        UUID eventId, String eventType, Instant occurredAt, String traceId, String source, T payload) {

    public EventEnvelope {
        requireField(eventId, "eventId");
        requireField(eventType, "eventType");
        requireField(occurredAt, "occurredAt");
        requireField(source, "source");
        requireField(payload, "payload");
    }

    /**
     * Tao envelope cho event moi: {@code eventId} la UUID ngau nhien, {@code occurredAt} lay tu {@code clock} (muc 4.2
     * convention). Producer dung method nay thay vi tu goi constructor.
     */
    public static <T> EventEnvelope<T> of(String eventType, String source, String traceId, T payload, Clock clock) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, Instant.now(clock), traceId, source, payload);
    }

    private static void requireField(Object value, String field) {
        if (value == null || value instanceof String text && text.isBlank()) {
            throw new IllegalArgumentException("EventEnvelope." + field + " is required");
        }
    }
}
