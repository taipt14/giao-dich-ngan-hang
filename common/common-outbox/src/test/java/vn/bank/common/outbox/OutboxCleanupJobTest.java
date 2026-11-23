package vn.bank.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OutboxCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");

    private final OutboxRepository repository = mock(OutboxRepository.class);
    private final OutboxCleanupJob job = new OutboxCleanupJob(
            repository,
            new OutboxProperties(100, 10, Duration.ofSeconds(10), Duration.ofDays(7)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void should_deleteSentEventsOlderThanSevenDays_when_cleanupRuns() {
        // given
        var cutoff = Instant.parse("2026-11-16T03:00:00Z");
        when(repository.deleteSentBefore(cutoff)).thenReturn(3);

        // when
        var deleted = job.cleanup();

        // then
        assertThat(deleted).isEqualTo(3);
        verify(repository).deleteSentBefore(cutoff);
    }
}
