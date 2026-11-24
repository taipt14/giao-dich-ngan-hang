package vn.bank.common.outbox;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gui event NEW trong {@code OUTBOX_EVENT} len Kafka. Moi batch mot transaction: khoa dong bang
 * {@code SELECT ... FOR UPDATE SKIP LOCKED}, gui, cho Kafka xac nhan roi danh dau SENT. Nhieu instance chay cung luc
 * khong lay trung dong cua nhau, nen o dieu kien binh thuong moi event chi gui mot lan.
 *
 * <p>Day la ngoai le co chu dich cua muc 8 convention (khong gui Kafka trong transaction DB): khoa dong phai giu den
 * khi danh dau SENT thi instance khac moi khong gui trung. Transaction chi khoa dong outbox, khong khoa du lieu nghiep
 * vu. Kafka xac nhan nhung commit loi thi event duoc gui lai o lan sau (at-least-once): consumer phai idempotent.
 */
@Slf4j
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final OutboxSender outboxSender;
    private final TransactionTemplate transactionTemplate;
    private final OutboxProperties properties;
    private final Clock clock;

    OutboxPublisher(
            OutboxRepository outboxRepository,
            OutboxSender outboxSender,
            TransactionTemplate transactionTemplate,
            OutboxProperties properties,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.outboxSender = outboxSender;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    /** Gui mot batch (toi da {@code bank.outbox.batch-size} event); tra so event da gui thanh cong. */
    public int publishBatch() {
        var sent = transactionTemplate.execute(status -> publishLockedBatch());
        return sent == null ? 0 : sent;
    }

    private int publishLockedBatch() {
        var batch = outboxRepository.lockNextBatch(properties.batchSize());
        if (batch.isEmpty()) {
            return 0;
        }
        var deliveries = send(batch);
        // nanoTime chi do khoang thoi gian cho, khong phai thoi diem nghiep vu nen khong can Clock
        var deadline = System.nanoTime() + properties.sendTimeout().toNanos();
        var sentCount = 0;
        for (var delivery : deliveries) {
            var failure = await(delivery.future(), deadline);
            if (failure.isEmpty()) {
                outboxRepository.markSent(delivery.event().eventId(), clock.instant());
                sentCount++;
            } else {
                recordFailure(delivery.event(), failure.get());
            }
        }
        log.info("Outbox published {} of {} events", sentCount, batch.size());
        return sentCount;
    }

    /**
     * Gui lan luot, khong cho tung event. Event gui loi ngay (vi du khong lay duoc metadata vi Kafka chet) thi dung
     * batch: cac event sau giu NEW va khong bi tinh mot lan loi, moi lan gui con lai cung se cho het timeout.
     */
    private List<Delivery> send(List<OutboxEvent> batch) {
        var deliveries = new ArrayList<Delivery>();
        for (var event : batch) {
            var future = outboxSender.send(event.topic(), event.aggregateId(), event.payload());
            deliveries.add(new Delivery(event, future));
            if (future.isCompletedExceptionally()) {
                break;
            }
        }
        return deliveries;
    }

    private void recordFailure(OutboxEvent event, Throwable failure) {
        var attempts = event.retryCount() + 1;
        if (attempts >= properties.maxAttempts()) {
            outboxRepository.markAttemptFailed(event.eventId(), attempts, OutboxStatus.FAILED);
            log.error(
                    "Outbox event {} {} FAILED after {} attempts, needs manual resend",
                    event.eventType(),
                    event.eventId(),
                    attempts,
                    failure);
            return;
        }
        outboxRepository.markAttemptFailed(event.eventId(), attempts, OutboxStatus.NEW);
        log.warn(
                "Failed to publish outbox event {} {} (attempt {}/{}), will retry: {}",
                event.eventType(),
                event.eventId(),
                attempts,
                properties.maxAttempts(),
                failure.toString());
    }

    private static Optional<Throwable> await(CompletableFuture<?> future, long deadlineNanos) {
        try {
            future.get(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
            return Optional.empty();
        } catch (ExecutionException e) {
            return Optional.of(e.getCause());
        } catch (TimeoutException e) {
            return Optional.of(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.of(e);
        }
    }

    private record Delivery(OutboxEvent event, CompletableFuture<?> future) {}
}
