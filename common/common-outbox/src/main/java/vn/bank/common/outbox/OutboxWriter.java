package vn.bank.common.outbox;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.bank.common.events.EventEnvelope;
import vn.bank.common.events.EventJson;

/**
 * Ghi event vao {@code OUTBOX_EVENT} trong transaction nghiep vu dang mo (muc 8 convention): transaction commit thi
 * event chac chan duoc {@link OutboxPublisher} gui, rollback thi dong outbox bien mat cung du lieu nghiep vu.
 *
 * <p>Producer cua service goi {@link #write} thay cho {@code KafkaTemplate.send}.
 */
public class OutboxWriter {

    private final OutboxRepository outboxRepository;

    OutboxWriter(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    /**
     * Luu {@code event} se gui len {@code topic} voi key {@code aggregateId}. Goi ngoai transaction →
     * {@link IllegalStateException}: khi do dong outbox tu commit rieng, mat dam bao "gui khi va chi khi commit".
     *
     * @param aggregateType loai aggregate, vi du {@code CUSTOMER}, {@code ACCOUNT}, {@code TRANSFER}
     * @param aggregateId id aggregate, dung lam key message de giu thu tu theo aggregate
     */
    public void write(String aggregateType, String aggregateId, String topic, EventEnvelope<?> event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Outbox event " + event.eventType() + " must be written inside a transaction");
        }
        outboxRepository.insert(new OutboxEvent(
                event.eventId(),
                aggregateType,
                aggregateId,
                topic,
                event.eventType(),
                EventJson.mapper().writeValueAsString(event),
                0,
                event.occurredAt()));
    }
}
