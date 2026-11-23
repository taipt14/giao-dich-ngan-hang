package vn.bank.common.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Chay {@link OutboxPublisher} moi {@code bank.outbox.poll-interval} (mac dinh 1 giay, tinh tu luc lan truoc xong).
 * Batch day thi quet tiep ngay, khong doi chu ky sau, de khong don event khi luu luong cao.
 */
@Slf4j
class OutboxPublisherJob {

    private final OutboxPublisher outboxPublisher;
    private final OutboxProperties properties;

    OutboxPublisherJob(OutboxPublisher outboxPublisher, OutboxProperties properties) {
        this.outboxPublisher = outboxPublisher;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${bank.outbox.poll-interval:1s}")
    void run() {
        try {
            int sent;
            do {
                sent = outboxPublisher.publishBatch();
            } while (sent == properties.batchSize());
        } catch (DataAccessException e) {
            // Ranh gioi job: DB loi thi lan chay sau thu lai, event van nam trong outbox
            log.error("Outbox publish cycle failed", e);
        }
    }
}
