package vn.bank.gateway.infrastructure.ratelimit;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.AbstractRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Mono;

/**
 * Rate limiter cua filter {@code RequestRateLimiter}: toi da {@code limit} request trong moi cua so truot
 * {@code window} (cau hinh {@code sliding-window-rate-limiter.*} theo route). Khong dung token bucket
 * {@code RedisRateLimiter} cua framework vi bucket day lai lien tuc: "10 request/phut" thanh 10 request lien tiep roi
 * them 1 request moi 6 giay, mot phut co the qua toi 20 request.
 *
 * <p>Dem bang sorted set trong mot script Lua (atomic, muc 12 convention), thoi gian lay tu Redis de moi instance
 * gateway dung chung mot dong ho. Request bi tu choi khong duoc dem, de spam khong keo dai thoi gian bi chan.
 *
 * <p>Redis loi → cho request di qua (fail-open), log WARN: rate limit chi chong spam, Redis khong phai nguon du lieu
 * goc, con kiem soat bat buoc (OTP, idempotency, han muc tien) nam o service phia sau.
 */
@Slf4j
public class SlidingWindowRateLimiter extends AbstractRateLimiter<SlidingWindowRateLimiter.Config> {

    public static final String CONFIGURATION_PROPERTY_NAME = "sliding-window-rate-limiter";
    public static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    /** KEYS[1] = key; ARGV = limit, do dai cua so (ms), id duy nhat cua request. Tra ve {allowed (1/0), remaining}. */
    private static final String SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window_ms = tonumber(ARGV[2])
            local time = redis.call('TIME')
            local now_ms = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            redis.call('ZREMRANGEBYSCORE', key, '-inf', now_ms - window_ms)
            local count = redis.call('ZCARD', key)
            if count >= limit then
              return {0, 0}
            end
            redis.call('ZADD', key, now_ms, ARGV[3])
            redis.call('PEXPIRE', key, window_ms)
            return {1, limit - count - 1}
            """;

    private static final long ALLOWED = 1L;
    private static final String UNKNOWN_REMAINING = "-1";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<List<Long>> script;

    public SlidingWindowRateLimiter(
            ReactiveStringRedisTemplate redisTemplate,
            RedisScript<List<Long>> script,
            ConfigurationService configurationService) {
        super(Config.class, CONFIGURATION_PROPERTY_NAME, configurationService);
        this.redisTemplate = redisTemplate;
        this.script = script;
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        var config = routeConfig(routeId);
        var args = List.of(
                String.valueOf(config.getLimit()),
                String.valueOf(config.getWindow().toMillis()),
                UUID.randomUUID().toString());
        return redisTemplate
                .execute(script, List.of(RedisKeys.rateLimit(routeId, id)), args)
                .flatMapIterable(Function.identity())
                .collectList()
                .map(result -> new Response(
                        result.get(0) == ALLOWED, remainingHeader(result.get(1).toString())))
                .onErrorResume(e -> {
                    log.warn("Rate limiter unavailable for route {}, request allowed: {}", routeId, e.getMessage());
                    return Mono.just(new Response(true, remainingHeader(UNKNOWN_REMAINING)));
                });
    }

    private Config routeConfig(String routeId) {
        var config = getConfig().get(routeId);
        if (config == null) {
            throw new IllegalArgumentException("No " + CONFIGURATION_PROPERTY_NAME + " configuration for " + routeId);
        }
        return config;
    }

    /** Script dem request, truyen vao constructor (de test dung Redis rieng cung dung dung script nay). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static RedisScript<List<Long>> script() {
        // Script Lua tra ve mang so nguyen → List<Long>
        return (RedisScript) RedisScript.of(SCRIPT, List.class);
    }

    private static Map<String, String> remainingHeader(String remaining) {
        return Map.of(REMAINING_HEADER, remaining);
    }

    /** Han muc cua mot route. JavaBean (khong phai record) vi gateway tao bang constructor rong roi bind thuoc tinh. */
    public static class Config {

        @Min(1)
        private int limit;

        @NotNull
        private Duration window;

        public int getLimit() {
            return limit;
        }

        public Config setLimit(int limit) {
            this.limit = limit;
            return this;
        }

        public Duration getWindow() {
            return window;
        }

        public Config setWindow(Duration window) {
            this.window = window;
            return this;
        }
    }
}
