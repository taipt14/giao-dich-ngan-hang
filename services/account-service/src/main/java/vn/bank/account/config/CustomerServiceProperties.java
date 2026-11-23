package vn.bank.account.config;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Dia chi customer-service cho API noi bo {@code /internal/customers}. Timeout dat chung qua spring.http.clients. */
@Validated
@ConfigurationProperties(prefix = "bank.account.customer-service")
public record CustomerServiceProperties(@NotNull URI baseUrl) {}
