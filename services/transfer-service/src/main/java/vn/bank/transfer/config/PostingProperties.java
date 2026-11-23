package vn.bank.transfer.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Retry khi goi {@code POST /internal/postings} bi loi ky thuat. {@code maxRetries} la so lan goi lai, khong tinh lan
 * goi dau; {@code retryWait} la khoang cho giua hai lan goi.
 */
@Validated
@ConfigurationProperties(prefix = "bank.transfer.posting")
public record PostingProperties(
        @Min(0) int maxRetries, @NotNull Duration retryWait) {}
