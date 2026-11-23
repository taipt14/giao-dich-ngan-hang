package vn.bank.gateway.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import vn.bank.gateway.support.RedisTestContainer;

/** Cua so truot cua {@link SlidingWindowRateLimiter} voi Redis that, cua so ngan de khong phai cho ca phut. */
class SlidingWindowRateLimiterIT {

    private static final String ROUTE_ID = "test-route";
    private static final int LIMIT = 2;
    private static final Duration WINDOW = Duration.ofSeconds(1);

    private static LettuceConnectionFactory connectionFactory;
    private static SlidingWindowRateLimiter rateLimiter;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(RedisTestContainer.host(), RedisTestContainer.port()));
        connectionFactory.afterPropertiesSet();
        // Khong co ConfigurationService: cau hinh route dat thang vao getConfig() thay vi bind tu application.yml
        rateLimiter = new SlidingWindowRateLimiter(
                new ReactiveStringRedisTemplate(connectionFactory), SlidingWindowRateLimiter.script(), null);
        rateLimiter
                .getConfig()
                .put(
                        ROUTE_ID,
                        new SlidingWindowRateLimiter.Config().setLimit(LIMIT).setWindow(WINDOW));
    }

    @AfterAll
    static void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void should_rejectRequest_when_limitReachedWithinWindow() {
        // given
        var userId = newUserId();
        isAllowed(userId);
        isAllowed(userId);

        // when
        var response = rateLimiter.isAllowed(ROUTE_ID, userId).block();

        // then
        assertThat(response.isAllowed()).isFalse();
        assertThat(response.getHeaders()).containsEntry(SlidingWindowRateLimiter.REMAINING_HEADER, "0");
    }

    @Test
    void should_allowAgain_when_windowSlidesPastEarlierRequests() {
        // given: dung het han muc, request bi tu choi khong duoc dem
        var userId = newUserId();
        isAllowed(userId);
        isAllowed(userId);
        assertThat(isAllowed(userId)).isFalse();

        // when / then
        await().atMost(WINDOW.multipliedBy(3)).until(() -> isAllowed(userId));
    }

    private boolean isAllowed(String userId) {
        return rateLimiter.isAllowed(ROUTE_ID, userId).block().isAllowed();
    }

    private static String newUserId() {
        return UUID.randomUUID().toString();
    }
}
