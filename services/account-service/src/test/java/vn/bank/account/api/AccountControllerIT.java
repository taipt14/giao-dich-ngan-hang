package vn.bank.account.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import vn.bank.account.domain.AccountStatus;
import vn.bank.account.domain.AccountType;
import vn.bank.account.support.FakeCustomerService;
import vn.bank.common.error.ErrorResponse;
import vn.bank.common.events.EventJson;
import vn.bank.common.events.Topics;
import vn.bank.common.test.AbstractIntegrationTest;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AccountControllerIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-21T03:00:00Z");
    private static final String ACCOUNTS_PATH = "/api/v1/accounts";
    private static final String ROLES_HEADER = "X-Roles";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(30);
    private static final FakeCustomerService CUSTOMER_SERVICE = FakeCustomerService.start();

    private final JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    private RestClient client;

    AccountControllerIT(@Autowired JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @DynamicPropertySource
    static void customerServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("bank.account.customer-service.base-url", CUSTOMER_SERVICE::uri);
    }

    @AfterAll
    static void stopCustomerService() {
        CUSTOMER_SERVICE.close();
    }

    @BeforeEach
    void createClient() {
        // Khong nem exception voi 4xx/5xx de test doc duoc body loi
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
                .build();
    }

    @Test
    void should_return201WithAccountNo_when_adminOpensAccountForActiveCustomer() {
        // given
        var cif = givenCustomer("ACTIVE");

        // when
        var response = open(cif, "ADMIN", AccountResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var account = response.getBody();
        assertThat(account).isNotNull();
        assertThat(account.accountNo()).matches("1000\\d{8}");
        assertThat(account.cif()).isEqualTo(cif);
        assertThat(account.accountType()).isEqualTo(AccountType.CURRENT);
        assertThat(account.currency()).isEqualTo("VND");
        assertThat(account.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.availableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.createdAt()).isEqualTo(NOW);
    }

    @Test
    void should_generateDifferentAccountNos_when_openingSeveralAccounts() {
        // given
        var cif = givenCustomer("ACTIVE");

        // when
        var first = open(cif, "ADMIN", AccountResponse.class).getBody().accountNo();
        var second = open(cif, "ADMIN", AccountResponse.class).getBody().accountNo();

        // then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void should_return404CustomerNotFound_when_customerDoesNotExist() {
        // given
        var cif = randomCif();

        // when
        var response = open(cif, "ADMIN", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND");
        assertThat(countAccountsOf(cif)).isZero();
    }

    @Test
    void should_return422CustomerNotActive_when_customerLocked() {
        // given
        var cif = givenCustomer("LOCKED");

        // when
        var response = open(cif, "ADMIN", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "CUSTOMER_NOT_ACTIVE");
        assertThat(countAccountsOf(cif)).isZero();
    }

    @Test
    void should_return422AccountLimitExceeded_when_openingFourthAccount() {
        // given
        var cif = givenCustomer("ACTIVE");
        for (var i = 0; i < 3; i++) {
            assertThat(open(cif, "ADMIN", AccountResponse.class).getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
        }

        // when
        var response = open(cif, "ADMIN", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "ACCOUNT_LIMIT_EXCEEDED");
        assertThat(countAccountsOf(cif)).isEqualTo(3);
    }

    @Test
    void should_publishAccountOpenedEvent_when_accountOpened() {
        // given
        var cif = givenCustomer("ACTIVE");

        try (var consumer = createKafkaTestConsumer(Topics.ACCOUNT_EVENTS)) {
            // when
            var accountNo = open(cif, "ADMIN", AccountResponse.class).getBody().accountNo();

            // then
            var event = EventJson.mapper()
                    .readTree(consumer.getRecordByKey(accountNo, EVENT_TIMEOUT).value());
            assertThat(event.get("eventType").asString()).isEqualTo("AccountOpened");
            assertThat(event.get("source").asString()).isEqualTo("account-service");
            assertThat(event.get("eventId").asString()).isNotBlank();
            assertThat(event.get("occurredAt").asString()).isEqualTo("2026-11-21T03:00:00Z");
            var payload = event.get("payload");
            assertThat(payload.get("accountNo").asString()).isEqualTo(accountNo);
            assertThat(payload.get("cif").asString()).isEqualTo(cif);
            assertThat(payload.get("accountType").asString()).isEqualTo("CURRENT");
            assertThat(payload.get("currency").asString()).isEqualTo("VND");
            assertThat(payload.get("status").asString()).isEqualTo("ACTIVE");
            assertThat(payload.get("openedAt").asString()).isEqualTo("2026-11-21T03:00:00Z");
        }
    }

    @Test
    void should_return503_when_customerServiceFails() {
        // given
        var cif = randomCif();
        CUSTOMER_SERVICE.givenFailure(cif);

        // when
        var response = open(cif, "ADMIN", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.SERVICE_UNAVAILABLE, "CUSTOMER_SERVICE_UNAVAILABLE");
    }

    @Test
    void should_return403_when_customerRoleOpensAccount() {
        // given
        var cif = givenCustomer("ACTIVE");

        // when
        var response = open(cif, "CUSTOMER", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertThat(countAccountsOf(cif)).isZero();
    }

    @Test
    void should_returnValidationFailed_when_cifMalformed() {
        // given
        var body = """
                {"cif":"12345","accountType":"CURRENT"}""";

        // when
        var response = post(body, "ADMIN", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(response.getBody().details())
                .anySatisfy(detail -> assertThat(detail.field()).isEqualTo("cif"));
    }

    @Test
    void should_return400_when_accountTypeNotSupported() {
        // given
        var body = """
                {"cif":"%s","accountType":"SAVINGS"}""".formatted(givenCustomer("ACTIVE"));

        // when
        var response = post(body, "ADMIN", ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_rejectNegativeBalance_when_updatingAccountRowDirectly() {
        // given
        var accountNo = open(givenCustomer("ACTIVE"), "ADMIN", AccountResponse.class)
                .getBody()
                .accountNo();

        // when
        var thrown = catchThrowable(
                () -> jdbcTemplate.update("UPDATE ACCOUNT SET BALANCE = -1 WHERE ACCOUNT_NO = ?", accountNo));

        // then
        assertThat(thrown)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_ACCOUNT_BALANCE");
    }

    private <T> ResponseEntity<T> open(String cif, String roles, Class<T> responseType) {
        return post("""
                {"cif":"%s","accountType":"CURRENT"}""".formatted(cif), roles, responseType);
    }

    private <T> ResponseEntity<T> post(String body, String roles, Class<T> responseType) {
        return client.post()
                .uri(ACCOUNTS_PATH)
                .header(USER_ID_HEADER, "test-admin")
                .header(ROLES_HEADER, roles)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(responseType);
    }

    private Integer countAccountsOf(String cif) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ACCOUNT WHERE CIF = ?", Integer.class, cif);
    }

    private static void assertError(ResponseEntity<ErrorResponse> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(code);
    }

    private static String givenCustomer(String status) {
        var cif = randomCif();
        CUSTOMER_SERVICE.givenCustomer(cif, status);
        return cif;
    }

    // Container Oracle co the duoc dung lai giua cac lan chay: moi test tu sinh CIF rieng
    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }
}
