package vn.bank.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.bank.gateway.support.GatewayTestClient.json;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.bank.gateway.infrastructure.ratelimit.RedisKeys;
import vn.bank.gateway.support.FakeBackendService;
import vn.bank.gateway.support.GatewayTestClient;
import vn.bank.gateway.support.RedisTestContainer;
import vn.bank.gateway.support.TestJwtIssuer;

/**
 * Rate limit theo user id tai gateway (BANK-012) voi Redis that. Key Redis dung chung giua cac test nen moi test dung
 * user id ngau nhien rieng.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GatewayRateLimitIT {

    private static final String TRANSFER_PATH = "/api/v1/transfers";
    private static final String CUSTOMER_PATH = "/api/v1/customers/me";
    private static final String TRANSFER_ROUTE_ID = "transfer-service";
    private static final int TRANSFER_LIMIT_PER_MINUTE = 10;
    private static final int DEFAULT_LIMIT_PER_MINUTE = 100;

    private static final TestJwtIssuer JWT_ISSUER = TestJwtIssuer.start();
    private static final FakeBackendService CUSTOMER_SERVICE = FakeBackendService.start("customer-service");
    private static final FakeBackendService TRANSFER_SERVICE = FakeBackendService.start("transfer-service");

    @LocalServerPort
    private int port;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    private GatewayTestClient gateway;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> TestJwtIssuer.ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", JWT_ISSUER::jwkSetUri);
        registry.add("CUSTOMER_SERVICE_URI", CUSTOMER_SERVICE::uri);
        registry.add("TRANSFER_SERVICE_URI", TRANSFER_SERVICE::uri);
        registry.add("spring.data.redis.host", RedisTestContainer::host);
        registry.add("spring.data.redis.port", RedisTestContainer::port);
    }

    @AfterAll
    static void stopFakeServers() {
        JWT_ISSUER.close();
        CUSTOMER_SERVICE.close();
        TRANSFER_SERVICE.close();
    }

    @BeforeEach
    void setUp() {
        gateway = new GatewayTestClient(port);
    }

    @Test
    void should_return429WithRateLimitExceeded_when_eleventhTransferRequestWithinOneMinute() {
        // given
        var token = JWT_ISSUER.customerToken(newUserId());

        // when
        var statuses = sendRequests(TRANSFER_PATH, token, TRANSFER_LIMIT_PER_MINUTE);
        var eleventh = gateway.getWithToken(TRANSFER_PATH, token);

        // then
        assertThat(statuses).hasSize(TRANSFER_LIMIT_PER_MINUTE).containsOnly(200);
        assertThat(eleventh.statusCode()).isEqualTo(429);
        assertThat(eleventh.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        var body = json(eleventh);
        assertThat(body.path("code").asString()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(body.path("message").asString()).isNotBlank();
        assertThat(Instant.parse(body.path("timestamp").asString())).isNotNull();
        assertThat(body.path("details").isArray()).isTrue();
    }

    @Test
    void should_notAffectOtherUserQuota_when_oneUserExceedsTransferLimit() {
        // given: user A dung het han muc chuyen khoan
        var tokenOfA = JWT_ISSUER.customerToken(newUserId());
        var tokenOfB = JWT_ISSUER.customerToken(newUserId());
        sendRequests(TRANSFER_PATH, tokenOfA, TRANSFER_LIMIT_PER_MINUTE);
        assertThat(gateway.getWithToken(TRANSFER_PATH, tokenOfA).statusCode()).isEqualTo(429);

        // when
        var statusesOfB = sendRequests(TRANSFER_PATH, tokenOfB, TRANSFER_LIMIT_PER_MINUTE);

        // then
        assertThat(statusesOfB).hasSize(TRANSFER_LIMIT_PER_MINUTE).containsOnly(200);
    }

    @Test
    void should_return429_when_otherApiExceeds100RequestsWithinOneMinute() {
        // given
        var token = JWT_ISSUER.customerToken(newUserId());

        // when
        var statuses = sendRequests(CUSTOMER_PATH, token, DEFAULT_LIMIT_PER_MINUTE);
        var exceeded = gateway.getWithToken(CUSTOMER_PATH, token);

        // then
        assertThat(statuses).hasSize(DEFAULT_LIMIT_PER_MINUTE).containsOnly(200);
        assertThat(exceeded.statusCode()).isEqualTo(429);
        assertThat(json(exceeded).path("code").asString()).isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void should_storeRateLimitKeyWithTtl_when_requestCounted() {
        // given
        var userId = newUserId();

        // when
        gateway.getWithToken(TRANSFER_PATH, JWT_ISSUER.customerToken(userId));

        // then
        var key = RedisKeys.rateLimit(TRANSFER_ROUTE_ID, userId);
        assertThat(redisTemplate.opsForZSet().size(key).block()).isEqualTo(1L);
        assertThat(redisTemplate.getExpire(key).block()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1));
    }

    private List<Integer> sendRequests(String path, String token, int count) {
        var statuses = new ArrayList<Integer>();
        for (var i = 0; i < count; i++) {
            statuses.add(gateway.getWithToken(path, token).statusCode());
        }
        return statuses;
    }

    private static String newUserId() {
        return UUID.randomUUID().toString();
    }
}
