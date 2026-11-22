package vn.bank.common.events;

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

    private static void requireField(Object value, String field) {
        if (value == null || value instanceof String text && text.isBlank()) {
            throw new IllegalArgumentException("EventEnvelope." + field + " is required");
        }
    }
}
