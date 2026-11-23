package vn.bank.common.outbox;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Cau hinh outbox dung chung cho moi service ({@code bank.outbox.*}, khong theo ten service vi la thu vien chung).
 * Chu ky chay job doc truc tiep trong {@code @Scheduled}: {@code bank.outbox.poll-interval} (mac dinh 1 giay) va
 * {@code bank.outbox.cleanup-interval} (mac dinh 1 gio).
 *
 * @param batchSize so event toi da moi lan quet
 * @param maxAttempts gui loi den lan thu nay thi chuyen FAILED
 * @param sendTimeout thoi gian toi da cho Kafka xac nhan mot batch
 * @param retention event SENT cu hon khoang nay bi xoa
 */
@Validated
@ConfigurationProperties(prefix = "bank.outbox")
public record OutboxProperties(
        @DefaultValue("100") @Min(1) int batchSize,
        @DefaultValue("10") @Min(1) int maxAttempts,
        @DefaultValue("10s") @NotNull Duration sendTimeout,
        @DefaultValue("7d") @NotNull Duration retention) {}
