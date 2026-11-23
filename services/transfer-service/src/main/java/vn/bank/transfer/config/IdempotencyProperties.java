package vn.bank.transfer.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Thoi gian song cua key {@code idem:{cif}:{idempotencyKey}} trong Redis (BANK-041). */
@Validated
@ConfigurationProperties(prefix = "bank.transfer.idempotency")
public record IdempotencyProperties(@NotNull Duration ttl) {}
