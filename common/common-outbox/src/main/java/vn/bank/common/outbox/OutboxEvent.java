package vn.bank.common.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Mot dong {@code OUTBOX_EVENT}. {@code payload} la JSON cua ca {@code EventEnvelope} (gui nguyen van lam value message
 * Kafka), {@code aggregateId} la key message.
 */
record OutboxEvent(
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String topic,
        String eventType,
        String payload,
        int retryCount,
        Instant createdAt) {}
