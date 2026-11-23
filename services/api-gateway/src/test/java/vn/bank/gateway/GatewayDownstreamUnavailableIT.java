package vn.bank.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.bank.gateway.support.GatewayTestClient.json;

import java.time.Instant;
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

/** Service phia sau khong ket noi duoc → gateway tra 503 theo format ErrorResponse. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GatewayDownstreamUnavailableIT {

    private static final TestJwtIssuer JWT_ISSUER = TestJwtIssuer.start();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> TestJwtIssuer.ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", JWT_ISSUER::jwkSetUri);
        registry.add("CUSTOMER_SERVICE_URI", FakeBackendService::unreachableUri);
    }

    @AfterAll
    static void stopFakeServers() {
        JWT_ISSUER.close();
    }

    @Test
    void should_return503WithErrorResponse_when_downstreamServiceUnreachable() {
        // given
        var gateway = new GatewayTestClient(port);

        // when
        var response = gateway.getWithToken("/api/v1/customers/me", JWT_ISSUER.customerToken());

        // then
        assertThat(response.statusCode()).isEqualTo(503);
        var body = json(response);
        assertThat(body.path("code").asString()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(Instant.parse(body.path("timestamp").asString())).isNotNull();
    }
}
