package vn.bank.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.bank.gateway.support.GatewayTestClient.json;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.bank.gateway.support.FakeBackendService;
import vn.bank.gateway.support.GatewayTestClient;
import vn.bank.gateway.support.TestJwtIssuer;

/** Routing, xac thuc JWT va format loi cua gateway (BANK-011), voi Keycloak va service phia sau gia. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GatewayRoutingIT {

    private static final String CUSTOMER_PATH = "/api/v1/customers/me";

    private static final TestJwtIssuer JWT_ISSUER = TestJwtIssuer.start();
    private static final FakeBackendService CUSTOMER_SERVICE = FakeBackendService.start("customer-service");
    private static final FakeBackendService ACCOUNT_SERVICE = FakeBackendService.start("account-service");
    private static final FakeBackendService TRANSFER_SERVICE = FakeBackendService.start("transfer-service");

    @LocalServerPort
    private int port;

    private GatewayTestClient gateway;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> TestJwtIssuer.ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", JWT_ISSUER::jwkSetUri);
        registry.add("CUSTOMER_SERVICE_URI", CUSTOMER_SERVICE::uri);
        registry.add("ACCOUNT_SERVICE_URI", ACCOUNT_SERVICE::uri);
        registry.add("TRANSFER_SERVICE_URI", TRANSFER_SERVICE::uri);
    }

    @AfterAll
    static void stopFakeServers() {
        JWT_ISSUER.close();
        CUSTOMER_SERVICE.close();
        ACCOUNT_SERVICE.close();
        TRANSFER_SERVICE.close();
    }

    @BeforeEach
    void setUp() {
        gateway = new GatewayTestClient(port);
        CUSTOMER_SERVICE.reset();
        ACCOUNT_SERVICE.reset();
        TRANSFER_SERVICE.reset();
    }

    @Test
    void should_return401WithErrorResponse_when_tokenMissing() {
        // when
        var response = gateway.get(CUSTOMER_PATH);

        // then
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        var body = json(response);
        assertThat(body.path("code").asString()).isEqualTo("UNAUTHORIZED");
        assertThat(body.path("message").asString()).isNotBlank();
        assertThat(Instant.parse(body.path("timestamp").asString())).isNotNull();
        assertThat(body.path("details").isArray()).isTrue();
        assertThat(CUSTOMER_SERVICE.lastRequestHeaders()).isEmpty();
    }

    @Test
    void should_return401_when_tokenExpired() {
        // given
        var token = JWT_ISSUER.expiredCustomerToken();

        // when
        var response = gateway.getWithToken(CUSTOMER_PATH, token);

        // then
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(json(response).path("code").asString()).isEqualTo("UNAUTHORIZED");
        assertThat(CUSTOMER_SERVICE.lastRequestHeaders()).isEmpty();
    }

    @Test
    void should_return401_when_tokenNotIssuedForGateway() {
        // given
        var token = JWT_ISSUER.customerTokenForAudience("other-client");

        // when
        var response = gateway.getWithToken(CUSTOMER_PATH, token);

        // then
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(json(response).path("code").asString()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void should_forwardCifHeaderToCustomerService_when_tokenValid() {
        // given: client co gia mao header danh tinh
        var headers = Map.of(
                "Authorization", "Bearer " + JWT_ISSUER.customerToken(),
                "X-Cif", "C999999999",
                "X-Roles", "ADMIN");

        // when
        var response = gateway.get(CUSTOMER_PATH, headers);

        // then: header lay tu token, header client gui bi thay the
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).path("service").asString()).isEqualTo("customer-service");
        var received = CUSTOMER_SERVICE.lastRequestHeaders().orElseThrow();
        assertThat(received.get("X-Cif")).containsExactly(TestJwtIssuer.CUSTOMER_CIF);
        assertThat(received.get("X-User-Id")).containsExactly(TestJwtIssuer.CUSTOMER_SUBJECT);
        assertThat(received.get("X-Roles")).containsExactly(TestJwtIssuer.CUSTOMER_ROLE);
    }

    @ParameterizedTest
    @CsvSource({
        "/api/v1/customers/me, customer-service",
        "/api/v1/accounts/1000000001/balance, account-service",
        "/api/v1/transfers, transfer-service"
    })
    void should_routeToMatchingService_when_tokenValid(String path, String expectedService) {
        // given
        var token = JWT_ISSUER.customerToken();

        // when
        var response = gateway.getWithToken(path, token);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).path("service").asString()).isEqualTo(expectedService);
    }

    @Test
    void should_return404WithErrorResponse_when_noRouteMatches() {
        // given
        var token = JWT_ISSUER.customerToken();

        // when
        var response = gateway.getWithToken("/api/v1/unknown-resources", token);

        // then
        assertThat(response.statusCode()).isEqualTo(404);
        var body = json(response);
        assertThat(body.path("code").asString()).isEqualTo("NOT_FOUND");
        assertThat(Instant.parse(body.path("timestamp").asString())).isNotNull();
    }

    @Test
    void should_returnHealthWithoutToken_when_healthChecked() {
        // when
        var response = gateway.get("/actuator/health");

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).path("status").asString()).isEqualTo("UP");
    }
}
