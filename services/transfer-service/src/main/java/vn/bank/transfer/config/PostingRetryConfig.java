package vn.bank.transfer.config;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.bank.common.error.TechnicalException;

/**
 * Retry posting sang account-service: chi goi lai khi loi ky thuat ({@link TechnicalException}: timeout, 5xx, khong ket
 * noi duoc). Loi nghiep vu (4xx) la ket qua chac chan, khong goi lai.
 */
@Slf4j
@Configuration
class PostingRetryConfig {

    static final String POSTING_RETRY = "accountPosting";

    @Bean
    Retry postingRetry(PostingProperties properties) {
        var config = RetryConfig.custom()
                .maxAttempts(properties.maxRetries() + 1)
                .waitDuration(properties.retryWait())
                .retryExceptions(TechnicalException.class)
                .build();
        var retry = Retry.of(POSTING_RETRY, config);
        retry.getEventPublisher()
                .onRetry(event -> log.warn(
                        "Posting attempt {} failed, retrying: {}",
                        event.getNumberOfRetryAttempts(),
                        event.getLastThrowable().getMessage()));
        return retry;
    }
}
