package vn.bank.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.bank.gateway.support.FakeBackendService;
import vn.bank.gateway.support.GatewayTestClient;
import vn.bank.gateway.support.TestJwtIssuer;

/**
 * Redis khong ket noi duoc → rate limit bo qua (fail-open): request van toi service phia sau va health van UP. Kiem
 * soat nghiep vu bat buoc (OTP, idempotency, han muc tien) nam o transfer-service, khong o gateway.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GatewayRateLimitRedisUnavailableIT {

    private static final TestJwtIssuer JWT_ISSUER = TestJwtIssuer.start();
    private static final FakeBackendService TRANSFER_SERVICE = FakeBackendService.start("transfer-service");

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> TestJwtIssuer.ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", JWT_ISSUER::jwkSetUri);
        registry.add("TRANSFER_SERVICE_URI", TRANSFER_SERVICE::uri);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add(
                "spring.data.redis.port",
                () -> URI.create(FakeBackendService.unreachableUri()).getPort());
    }

    @AfterAll
    static void stopFakeServers() {
        JWT_ISSUER.close();
        TRANSFER_SERVICE.close();
    }

    @Test
    void should_forwardRequest_when_redisUnavailable() {
        // given
        var gateway = new GatewayTestClient(port);

        // when
        var response = gateway.getWithToken("/api/v1/transfers", JWT_ISSUER.customerToken());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(TRANSFER_SERVICE.lastRequestHeaders()).isPresent();
    }

    @Test
    void should_reportHealthUp_when_redisUnavailable() {
        // given
        var gateway = new GatewayTestClient(port);

        // when
        var response = gateway.get("/actuator/health");

        // then
        assertThat(response.statusCode()).isEqualTo(200);
    }
}
