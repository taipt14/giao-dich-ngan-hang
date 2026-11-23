package vn.bank.transfer.config;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Dia chi account-service cho API noi bo {@code /internal/accounts}. Timeout dat chung qua spring.http.clients. */
@Validated
@ConfigurationProperties(prefix = "bank.transfer.account-service")
public record AccountServiceProperties(@NotNull URI baseUrl) {}
