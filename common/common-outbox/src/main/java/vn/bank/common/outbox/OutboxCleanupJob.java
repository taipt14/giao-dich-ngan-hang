package vn.bank.common.outbox;

import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Xoa event SENT cu hon {@code bank.outbox.retention} (mac dinh 7 ngay) moi {@code bank.outbox.cleanup-interval} (mac
 * dinh 1 gio) de bang outbox khong phinh. Event NEW, FAILED khong bao gio bi xoa. Nhieu instance cung chay khong sao:
 * DELETE theo dieu kien, chay trung chi xoa 0 dong.
 */
@Slf4j
public class OutboxCleanupJob {

    private final OutboxRepository outboxRepository;
    private final OutboxProperties properties;
    private final Clock clock;

    OutboxCleanupJob(OutboxRepository outboxRepository, OutboxProperties properties, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${bank.outbox.cleanup-interval:1h}")
    void run() {
        try {
            cleanup();
        } catch (DataAccessException e) {
            // Ranh gioi job: lan chay sau thu lai
            log.error("Outbox cleanup failed", e);
        }
    }

    /** Xoa event SENT co SENT_AT truoc (hien tai - retention); tra so dong da xoa. */
    public int cleanup() {
        var cutoff = clock.instant().minus(properties.retention());
        var deleted = outboxRepository.deleteSentBefore(cutoff);
        if (deleted > 0) {
            log.info("Outbox cleanup deleted {} SENT events sent before {}", deleted, cutoff);
        }
        return deleted;
    }
}
