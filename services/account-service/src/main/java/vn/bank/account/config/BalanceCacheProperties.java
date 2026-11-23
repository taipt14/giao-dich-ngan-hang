package vn.bank.account.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Cache so du {@code balance:{accountNo}} trong Redis (BANK-021). */
@Validated
@ConfigurationProperties(prefix = "bank.account.balance-cache")
public record BalanceCacheProperties(@NotNull Duration ttl) {}
