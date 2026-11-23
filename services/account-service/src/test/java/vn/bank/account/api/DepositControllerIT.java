package vn.bank.account.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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
import vn.bank.common.error.ErrorResponse;
import vn.bank.common.events.EventJson;
import vn.bank.common.events.Topics;
import vn.bank.common.test.AbstractIntegrationTest;

/**
 * {@code POST /api/v1/accounts/{accountNo}/deposits} voi Oracle, Kafka, Redis that. LEDGER_ENTRY khong xoa duoc
 * (trigger chan) nen moi test dung tai khoan va Idempotency-Key rieng.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class DepositControllerIT extends AbstractIntegrationTest {

    private static final String DEPOSITS_PATH = "/api/v1/accounts/{accountNo}/deposits";
    private static final String ROLES_HEADER = "X-Roles";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String ADMIN = "ADMIN";
    private static final String CASH_VAULT = "CASH_VAULT";
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(30);

    private final JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    private RestClient client;

    DepositControllerIT(@Autowired JdbcTemplate jdbcTemplate) {
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
    void should_increaseBalanceByAmountWithTwoBalancedLedgerEntries_when_depositOneMillion() {
        // given
        var accountNo = givenAccount("ACTIVE");
        var key = newKey();

        // when
        var response = deposit(accountNo, key, body("1000000"), ADMIN, DepositResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var deposit = response.getBody();
        assertThat(deposit.accountNo()).isEqualTo(accountNo);
        assertThat(deposit.idempotencyKey()).isEqualTo(key);
        assertThat(deposit.amount()).isEqualTo(new BigDecimal("1000000.00"));
        assertThat(deposit.balanceAfter()).isEqualTo(new BigDecimal("1000000.00"));
        assertThat(balanceOf(accountNo)).isEqualByComparingTo("1000000.00");
        var entries = jdbcTemplate.queryForList(
                "SELECT ACCOUNT_NO, DIRECTION, AMOUNT FROM LEDGER_ENTRY WHERE JOURNAL_ID = ? ORDER BY ENTRY_ID",
                deposit.journalId());
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0)).containsEntry("ACCOUNT_NO", CASH_VAULT).containsEntry("DIRECTION", "DEBIT");
        assertThat(entries.get(1)).containsEntry("ACCOUNT_NO", accountNo).containsEntry("DIRECTION", "CREDIT");
        assertThat((BigDecimal) entries.get(0).get("AMOUNT")).isEqualByComparingTo("1000000.00");
        assertThat((BigDecimal) entries.get(1).get("AMOUNT")).isEqualByComparingTo("1000000.00");
        assertThat(journalReferenceType(key)).isEqualTo("DEPOSIT");
    }

    @Test
    void should_publishBalanceChanged_when_deposited() {
        // given
        var accountNo = givenAccount("ACTIVE");
        var key = newKey();

        try (var consumer = createKafkaTestConsumer(Topics.ACCOUNT_EVENTS)) {
            // when
            deposit(accountNo, key, body("250000.50"), ADMIN, DepositResponse.class);

            // then
            var record = consumer.getRecord(consumerRecord -> accountNo.equals(consumerRecord.key()), EVENT_TIMEOUT);
            var event = EventJson.mapper().readTree(record.value());
            assertThat(event.get("eventType").asString()).isEqualTo("BalanceChanged");
            var payload = event.get("payload");
            assertThat(payload.get("referenceType").asString()).isEqualTo("DEPOSIT");
            assertThat(payload.get("referenceId").asString()).isEqualTo(key);
            assertThat(payload.get("direction").asString()).isEqualTo("CREDIT");
            assertThat(payload.get("amount").decimalValue()).isEqualByComparingTo("250000.50");
            assertThat(payload.get("balanceAfter").decimalValue()).isEqualByComparingTo("250000.50");
            assertThat(payload.get("description").asString()).isEqualTo("Nap tien tai quay");
        }
    }

    @Test
    void should_notDepositTwiceAndReturnPreviousResult_when_sameIdempotencyKeyResent() {
        // given
        var accountNo = givenAccount("ACTIVE");
        var key = newKey();
        var body = body("1000000");

        // when
        var first = deposit(accountNo, key, body, ADMIN, DepositResponse.class);
        var second = deposit(accountNo, key, body, ADMIN, DepositResponse.class);

        // then
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Lan dau tra createdAt tu Clock (toi 100ns), lan sau doc tu cot TIMESTAMP(6): chi lech phan duoi micro giay
        assertThat(second.getBody())
                .usingRecursiveComparison()
                .ignoringFields("createdAt")
                .isEqualTo(first.getBody());
        assertThat(second.getBody().createdAt()).isCloseTo(first.getBody().createdAt(), within(1, ChronoUnit.MILLIS));
        assertThat(countJournals(key)).isEqualTo(1);
        assertThat(countEntries(accountNo)).isEqualTo(1);
        assertThat(balanceOf(accountNo)).isEqualByComparingTo("1000000.00");
    }

    @Test
    void should_return422IdempotencyKeyReused_when_sameKeyResentWithDifferentAmount() {
        // given
        var accountNo = givenAccount("ACTIVE");
        var key = newKey();
        deposit(accountNo, key, body("1000000"), ADMIN, DepositResponse.class);

        // when
        var response = deposit(accountNo, key, body("2000000"), ADMIN, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED");
        assertThat(countJournals(key)).isEqualTo(1);
        assertThat(balanceOf(accountNo)).isEqualByComparingTo("1000000.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOCKED", "CLOSED"})
    void should_return422AccountNotActive_when_accountLockedOrClosed(String status) {
        // given
        var accountNo = givenAccount(status);
        var key = newKey();

        // when
        var response = deposit(accountNo, key, body("1000000"), ADMIN, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "ACCOUNT_NOT_ACTIVE");
        assertThat(countJournals(key)).isZero();
        assertThat(countEntries(accountNo)).isZero();
        assertThat(balanceOf(accountNo)).isEqualByComparingTo("0.00");
    }

    @Test
    void should_acceptDeposit_when_amountEqualsMax() {
        // given
        var accountNo = givenAccount("ACTIVE");

        // when
        var response = deposit(accountNo, newKey(), body("500000000"), ADMIN, DepositResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(balanceOf(accountNo)).isEqualByComparingTo("500000000.00");
    }

    @Test
    void should_return422DepositLimitExceeded_when_amountAboveMax() {
        // given
        var accountNo = givenAccount("ACTIVE");
        var key = newKey();

        // when
        var response = deposit(accountNo, key, body("500000000.01"), ADMIN, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "DEPOSIT_LIMIT_EXCEEDED");
        assertThat(countJournals(key)).isZero();
        assertThat(balanceOf(accountNo)).isEqualByComparingTo("0.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1000", "100.001"})
    void should_return400_when_amountInvalid(String amount) {
        // given
        var accountNo = givenAccount("ACTIVE");
        var key = newKey();

        // when
        var response = deposit(accountNo, key, body(amount), ADMIN, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(countJournals(key)).isZero();
    }

    @Test
    void should_return400_when_idempotencyKeyMissing() {
        // given
        var accountNo = givenAccount("ACTIVE");

        // when
        var response = deposit(accountNo, null, body("1000"), ADMIN, ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(countEntries(accountNo)).isZero();
    }

    @Test
    void should_return400_when_idempotencyKeyTooLong() {
        // given
        var accountNo = givenAccount("ACTIVE");

        // when
        var response = deposit(accountNo, "k".repeat(65), body("1000"), ADMIN, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(countEntries(accountNo)).isZero();
    }

    @Test
    void should_return403_when_callerIsNotAdmin() {
        // given
        var accountNo = givenAccount("ACTIVE");

        // when
        var response = deposit(accountNo, newKey(), body("1000"), "CUSTOMER", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        assertThat(countEntries(accountNo)).isZero();
    }

    @Test
    void should_return404_when_accountDoesNotExist() {
        // when
        var response = deposit(randomAccountNo(), newKey(), body("1000"), ADMIN, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
    }

    @Test
    void should_return422AccountTypeNotAllowed_when_depositIntoGlAccount() {
        // given
        var key = newKey();

        // when
        var response = deposit("FEE_INCOME", key, body("1000"), ADMIN, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "ACCOUNT_TYPE_NOT_ALLOWED");
        assertThat(countJournals(key)).isZero();
    }

    private <T> ResponseEntity<T> deposit(
            String accountNo, String idempotencyKey, String body, String roles, Class<T> responseType) {
        return client.post()
                .uri(DEPOSITS_PATH, accountNo)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.set(ROLES_HEADER, roles);
                    if (idempotencyKey != null) {
                        headers.set(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
                    }
                })
                .body(body)
                .retrieve()
                .toEntity(responseType);
    }

    private static String body(String amount) {
        return """
                {"amount":%s,"description":"Nap tien tai quay"}""".formatted(amount);
    }

    private String givenAccount(String status) {
        var accountNo = randomAccountNo();
        jdbcTemplate.update("""
                INSERT INTO ACCOUNT (ACCOUNT_NO, CIF, ACCOUNT_TYPE, CURRENCY, BALANCE, AVAILABLE_BALANCE, STATUS,
                                     VERSION, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, 'CURRENT', 'VND', 0, 0, ?, 0, SYSTIMESTAMP, SYSTIMESTAMP)""", accountNo, randomCif(), status);
        return accountNo;
    }

    private BigDecimal balanceOf(String accountNo) {
        return jdbcTemplate.queryForObject(
                "SELECT BALANCE FROM ACCOUNT WHERE ACCOUNT_NO = ?", BigDecimal.class, accountNo);
    }

    private String journalReferenceType(String referenceId) {
        return jdbcTemplate.queryForObject(
                "SELECT REFERENCE_TYPE FROM JOURNAL WHERE REFERENCE_ID = ?", String.class, referenceId);
    }

    private Integer countJournals(String referenceId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM JOURNAL WHERE REFERENCE_ID = ?", Integer.class, referenceId);
    }

    private Integer countEntries(String accountNo) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM LEDGER_ENTRY WHERE ACCOUNT_NO = ?", Integer.class, accountNo);
    }

    private static void assertError(ResponseEntity<ErrorResponse> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(code);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    // Container Oracle co the duoc dung lai giua cac lan chay: moi test tu sinh du lieu rieng.
    // Tien to 9000 de khong trung so tai khoan sinh tu sequence (1000...) va cua cac IT khac (2000... den 8000...)
    private static String randomAccountNo() {
        return "9000%08d".formatted(ThreadLocalRandom.current().nextLong(1, 100_000_000L));
    }

    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }
}
