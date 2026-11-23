package vn.bank.gateway.config;

import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import vn.bank.gateway.infrastructure.ratelimit.SlidingWindowRateLimiter;

/**
 * Rate limiter cho filter {@code RequestRateLimiter} (han muc tung route trong application.yml). Key la user id lay tu
 * {@code principalNameKeyResolver} mac dinh cua gateway: ten cua JWT da xac thuc = claim {@code sub}, cung gia tri
 * header {@code X-User-Id}.
 */
@Configuration
class RateLimitConfig {

    // Auto-config cua gateway van tao them bean redisRateLimiter (token bucket, khong dung): @Primary de filter
    // RequestRateLimiter lay limiter nay lam mac dinh thay vi bao loi co hai bean RateLimiter
    @Bean
    @Primary
    SlidingWindowRateLimiter slidingWindowRateLimiter(
            ReactiveStringRedisTemplate redisTemplate, ConfigurationService configurationService) {
        return new SlidingWindowRateLimiter(redisTemplate, SlidingWindowRateLimiter.script(), configurationService);
    }
}
