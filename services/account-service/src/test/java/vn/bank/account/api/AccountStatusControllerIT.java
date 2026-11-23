package vn.bank.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import vn.bank.account.domain.AccountStatus;
import vn.bank.common.error.ErrorResponse;
import vn.bank.common.events.EventJson;
import vn.bank.common.events.Topics;
import vn.bank.common.test.AbstractIntegrationTest;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AccountStatusControllerIT extends AbstractIntegrationTest {

    private static final String STATUS_PATH = "/api/v1/accounts/{accountNo}/{action}";
    private static final String ROLES_HEADER = "X-Roles";
    private static final String REASON_BODY = """
            {"reason":"Yeu cau cua khach hang"}""";
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(30);

    private final JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    private RestClient client;

    AccountStatusControllerIT(@Autowired JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
    void should_lockAccountAndRecordHistory_when_adminLocksActiveAccount() {
        // given
        var accountNo = givenAccount("ACTIVE", "0.00");

        // when
        var response = post(accountNo, "lock", REASON_BODY, "ADMIN", AccountResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(AccountStatus.LOCKED);
        assertThat(statusOf(accountNo)).isEqualTo("LOCKED");
        var history = jdbcTemplate.queryForMap(
                "SELECT PREVIOUS_STATUS, STATUS, REASON FROM ACCOUNT_STATUS_HISTORY WHERE ACCOUNT_NO = ?", accountNo);
        assertThat(history)
                .containsEntry("PREVIOUS_STATUS", "ACTIVE")
                .containsEntry("STATUS", "LOCKED")
                .containsEntry("REASON", "Yeu cau cua khach hang");
    }

    @Test
    void should_unlockAccount_when_adminUnlocksLockedAccount() {
        // given
        var accountNo = givenAccount("LOCKED", "0.00");

        // when
        var response = post(accountNo, "unlock", REASON_BODY, "ADMIN", AccountResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(statusOf(accountNo)).isEqualTo("ACTIVE");
    }

    @Test
    void should_closeAccount_when_balanceIsZero() {
        // given
        var accountNo = givenAccount("LOCKED", "0.00");

        // when
        var response = post(accountNo, "close", REASON_BODY, "ADMIN", AccountResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusOf(accountNo)).isEqualTo("CLOSED");
        assertThat(countHistoryOf(accountNo)).isEqualTo(1);
    }

    @Test
    void should_return422AccountBalanceNotZero_when_closingAccountWithBalance() {
        // given
        var accountNo = givenAccount("ACTIVE", "150000.00");

        // when
        var response = post(accountNo, "close", REASON_BODY, "ADMIN", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "ACCOUNT_BALANCE_NOT_ZERO");
        assertThat(statusOf(accountNo)).isEqualTo("ACTIVE");
        assertThat(countHistoryOf(accountNo)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"lock", "unlock", "close"})
    void should_return422InvalidStatusTransition_when_accountClosed(String action) {
        // given
        var accountNo = givenAccount("CLOSED", "0.00");

        // when
        var response = post(accountNo, action, REASON_BODY, "ADMIN", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_STATUS_TRANSITION");
        assertThat(statusOf(accountNo)).isEqualTo("CLOSED");
        assertThat(countHistoryOf(accountNo)).isZero();
    }

    @Test
    void should_return422InvalidStatusTransition_when_lockingLockedAccount() {
        // given
        var accountNo = givenAccount("LOCKED", "0.00");

        // when
        var response = post(accountNo, "lock", REASON_BODY, "ADMIN", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_STATUS_TRANSITION");
    }

    @Test
    void should_publishAccountStatusChangedEvent_when_accountLocked() {
        // given
        var accountNo = givenAccount("ACTIVE", "0.00");

        try (var consumer = createKafkaTestConsumer(Topics.ACCOUNT_EVENTS)) {
            // when
            post(accountNo, "lock", REASON_BODY, "ADMIN", AccountResponse.class);

            // then
            var event = EventJson.mapper()
                    .readTree(consumer.getRecordByKey(accountNo, EVENT_TIMEOUT).value());
            assertThat(event.get("eventType").asString()).isEqualTo("AccountStatusChanged");
            assertThat(event.get("source").asString()).isEqualTo("account-service");
            var payload = event.get("payload");
            assertThat(payload.get("accountNo").asString()).isEqualTo(accountNo);
            assertThat(payload.get("previousStatus").asString()).isEqualTo("ACTIVE");
            assertThat(payload.get("status").asString()).isEqualTo("LOCKED");
            assertThat(payload.get("reason").asString()).isEqualTo("Yeu cau cua khach hang");
            assertThat(payload.get("changedAt").asString()).isNotBlank();
        }
    }

    @Test
    void should_return400_when_reasonMissing() {
        // given
        var accountNo = givenAccount("ACTIVE", "0.00");

        // when
        var response = post(accountNo, "lock", """
                {"reason":" "}""", "ADMIN", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(statusOf(accountNo)).isEqualTo("ACTIVE");
    }

    @Test
    void should_return403_when_customerRoleLocksAccount() {
        // given
        var accountNo = givenAccount("ACTIVE", "0.00");

        // when
        var response = post(accountNo, "lock", REASON_BODY, "CUSTOMER", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertThat(statusOf(accountNo)).isEqualTo("ACTIVE");
    }

    @Test
    void should_return404_when_accountDoesNotExist() {
        // when
        var response = post(randomAccountNo(), "lock", REASON_BODY, "ADMIN", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
    }

    private <T> ResponseEntity<T> post(
            String accountNo, String action, String body, String roles, Class<T> responseType) {
        return client.post()
                .uri(STATUS_PATH, accountNo, action)
                .header(ROLES_HEADER, roles)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(responseType);
    }

    private String givenAccount(String status, String balance) {
        var accountNo = randomAccountNo();
        var value = new BigDecimal(balance);
        jdbcTemplate.update("""
                INSERT INTO ACCOUNT (ACCOUNT_NO, CIF, ACCOUNT_TYPE, CURRENCY, BALANCE, AVAILABLE_BALANCE, STATUS,
                                     VERSION, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, 'CURRENT', 'VND', ?, ?, ?, 0, SYSTIMESTAMP, SYSTIMESTAMP)""", accountNo, randomCif(), value, value, status);
        return accountNo;
    }

    private String statusOf(String accountNo) {
        return jdbcTemplate.queryForObject("SELECT STATUS FROM ACCOUNT WHERE ACCOUNT_NO = ?", String.class, accountNo);
    }

    private Integer countHistoryOf(String accountNo) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ACCOUNT_STATUS_HISTORY WHERE ACCOUNT_NO = ?", Integer.class, accountNo);
    }

    private static void assertError(ResponseEntity<ErrorResponse> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(code);
    }

    // Container Oracle co the duoc dung lai giua cac lan chay: moi test tu sinh du lieu rieng.
    // Tien to 3000 de khong trung so tai khoan sinh tu sequence (1000...) va cua test khac (2000...)
    private static String randomAccountNo() {
        return "3000%08d".formatted(ThreadLocalRandom.current().nextLong(1, 100_000_000L));
    }

    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }
}
