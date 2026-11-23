package vn.bank.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import vn.bank.account.infrastructure.cache.RedisKeys;
import vn.bank.common.error.ErrorResponse;
import vn.bank.common.events.EventJson;
import vn.bank.common.events.Topics;
import vn.bank.common.test.AbstractIntegrationTest;
import vn.bank.common.test.KafkaTestConsumer;

/**
 * {@code POST /internal/postings} voi Oracle, Kafka, Redis that. LEDGER_ENTRY khong xoa duoc (trigger chan) nen moi
 * test dung tai khoan va referenceId rieng, va tai khoan nap tien bang posting DEPOSIT tu CASH_VAULT de tong so du
 * moi tai khoan van bang 0.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PostingInternalControllerIT extends AbstractIntegrationTest {

    private static final String POSTINGS_PATH = "/internal/postings";
    private static final String CASH_VAULT = "CASH_VAULT";
    private static final String FEE_INCOME = "FEE_INCOME";
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(30);

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    @LocalServerPort
    private int port;

    private RestClient client;

    PostingInternalControllerIT(@Autowired JdbcTemplate jdbcTemplate, @Autowired StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
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
    void should_return400JournalNotBalancedAndWriteNothing_when_journalNotBalanced() {
        // given
        var source = givenFundedAccount("1000000.00");
        var dest = givenAccount("ACTIVE");
        var referenceId = newReferenceId();
        var cashVaultBefore = balanceOf(CASH_VAULT);

        // when
        var response = post(
                transferBody(referenceId, line(source, "DEBIT", "300000.00"), line(dest, "CREDIT", "299999.99")),
                ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "JOURNAL_NOT_BALANCED");
        assertThat(countJournals(referenceId)).isZero();
        assertThat(balanceOf(source)).isEqualByComparingTo("1000000.00");
        assertThat(balanceOf(dest)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(CASH_VAULT)).isEqualByComparingTo(cashVaultBefore);
        assertThat(countEntries(source)).isEqualTo(1);
        assertThat(countEntries(dest)).isZero();
    }

    @Test
    void should_rollbackAllLines_when_oneAccountLocked() {
        // given: dong DEBIT hop le dung truoc dong CREDIT vao tai khoan LOCKED
        var source = givenFundedAccount("1000000.00");
        var lockedDest = givenAccount("LOCKED");
        var referenceId = newReferenceId();

        // when
        var response = post(
                transferBody(referenceId, line(source, "DEBIT", "300000.00"), line(lockedDest, "CREDIT", "300000.00")),
                ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "ACCOUNT_NOT_ACTIVE");
        assertThat(countJournals(referenceId)).isZero();
        assertThat(balanceOf(source)).isEqualByComparingTo("1000000.00");
        assertThat(balanceOf(lockedDest)).isEqualByComparingTo("0.00");
        assertThat(countEntries(source)).isEqualTo(1);
        assertThat(countEntries(lockedDest)).isZero();
    }

    @Test
    void should_createOnlyOneJournal_when_postedTwiceWithSameReferenceId() {
        // given
        var source = givenFundedAccount("1000000.00");
        var dest = givenAccount("ACTIVE");
        var referenceId = newReferenceId();
        var body = transferBody(referenceId, line(source, "DEBIT", "300000.00"), line(dest, "CREDIT", "300000.00"));

        // when
        var first = post(body, PostingResponse.class);
        var second = post(body, PostingResponse.class);

        // then
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().journalId()).isEqualTo(first.getBody().journalId());
        assertThat(second.getBody().entries()).isEqualTo(first.getBody().entries());
        assertThat(countJournals(referenceId)).isEqualTo(1);
        assertThat(balanceOf(source)).isEqualByComparingTo("700000.00");
        assertThat(balanceOf(dest)).isEqualByComparingTo("300000.00");
        assertThat(countEntries(dest)).isEqualTo(1);
    }

    @Test
    void should_matchBalanceWithLastLedgerBalanceAfter_when_posted() {
        // given: A chuyen 300.000 cho B, phi 2.200 (docs/ledger.md 4.2)
        var source = givenFundedAccount("1000000.00");
        var dest = givenAccount("ACTIVE");
        var referenceId = newReferenceId();

        // when
        var response = post(
                transferBody(
                        referenceId,
                        line(source, "DEBIT", "302200.00"),
                        line(dest, "CREDIT", "300000.00"),
                        line(FEE_INCOME, "CREDIT", "2200.00")),
                PostingResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().entries())
                .extracting(LedgerEntryResponse::accountNo)
                .containsExactly(source, dest, FEE_INCOME);
        assertThat(balanceOf(source)).isEqualByComparingTo("697800.00");
        assertThat(balanceOf(dest)).isEqualByComparingTo("300000.00");
        for (var accountNo : new String[] {source, dest, FEE_INCOME, CASH_VAULT}) {
            assertThat(lastBalanceAfter(accountNo)).as(accountNo).isEqualByComparingTo(balanceOf(accountNo));
        }
        assertThat(availableBalanceOf(source)).isEqualByComparingTo("697800.00");
    }

    @Test
    void should_publishBalanceChangedForCustomerAccounts_when_posted() {
        // given
        var source = givenFundedAccount("1000000.00");
        var dest = givenAccount("ACTIVE");
        var referenceId = newReferenceId();

        // Moi tai khoan mot consumer: findRecord bo qua cac message con lai trong lan poll da tim thay
        try (var destConsumer = createKafkaTestConsumer(Topics.ACCOUNT_EVENTS);
                var sourceConsumer = createKafkaTestConsumer(Topics.ACCOUNT_EVENTS)) {
            // when
            post(
                    transferBody(referenceId, line(source, "DEBIT", "300000.00"), line(dest, "CREDIT", "300000.00")),
                    PostingResponse.class);

            // then
            var event = EventJson.mapper()
                    .readTree(eventOf(destConsumer, dest, referenceId).value());
            assertThat(event.get("eventType").asString()).isEqualTo("BalanceChanged");
            assertThat(event.get("source").asString()).isEqualTo("account-service");
            var payload = event.get("payload");
            assertThat(payload.get("accountNo").asString()).isEqualTo(dest);
            assertThat(payload.get("referenceType").asString()).isEqualTo("TRANSFER");
            assertThat(payload.get("referenceId").asString()).isEqualTo(referenceId);
            assertThat(payload.get("direction").asString()).isEqualTo("CREDIT");
            assertThat(payload.get("amount").decimalValue()).isEqualByComparingTo("300000.00");
            assertThat(payload.get("balanceAfter").decimalValue()).isEqualByComparingTo("300000.00");
            assertThat(payload.get("description").asString()).isEqualTo("Chuyen tien IT");
            var sourceEvent = EventJson.mapper()
                    .readTree(eventOf(sourceConsumer, source, referenceId).value());
            assertThat(sourceEvent.get("payload").get("direction").asString()).isEqualTo("DEBIT");
            assertThat(sourceEvent.get("payload").get("balanceAfter").decimalValue())
                    .isEqualByComparingTo("700000.00");
        }
    }

    @Test
    void should_evictBalanceCache_when_balanceChanged() {
        // given
        var source = givenFundedAccount("1000000.00");
        var dest = givenAccount("ACTIVE");
        redisTemplate.opsForValue().set(RedisKeys.balance(source), "{}", Duration.ofMinutes(5));
        redisTemplate.opsForValue().set(RedisKeys.balance(dest), "{}", Duration.ofMinutes(5));

        // when
        post(
                transferBody(newReferenceId(), line(source, "DEBIT", "100.00"), line(dest, "CREDIT", "100.00")),
                PostingResponse.class);

        // then
        assertThat(redisTemplate.hasKey(RedisKeys.balance(source))).isFalse();
        assertThat(redisTemplate.hasKey(RedisKeys.balance(dest))).isFalse();
    }

    @Test
    void should_return422InsufficientBalance_when_debitExceedsBalance() {
        // given
        var source = givenFundedAccount("100.00");
        var dest = givenAccount("ACTIVE");
        var referenceId = newReferenceId();

        // when
        var response = post(
                transferBody(referenceId, line(source, "DEBIT", "100.01"), line(dest, "CREDIT", "100.01")),
                ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "INSUFFICIENT_BALANCE");
        assertThat(countJournals(referenceId)).isZero();
        assertThat(balanceOf(source)).isEqualByComparingTo("100.00");
    }

    @Test
    void should_return404_when_accountDoesNotExist() {
        // given
        var source = givenFundedAccount("1000.00");
        var referenceId = newReferenceId();

        // when
        var response = post(
                transferBody(referenceId, line(source, "DEBIT", "100.00"), line(randomAccountNo(), "CREDIT", "100.00")),
                ErrorResponse.class);

        // then
        assertError(response, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
        assertThat(countJournals(referenceId)).isZero();
        assertThat(balanceOf(source)).isEqualByComparingTo("1000.00");
    }

    @Test
    void should_return400_when_amountNotPositive() {
        // given
        var source = givenFundedAccount("1000.00");
        var dest = givenAccount("ACTIVE");
        var referenceId = newReferenceId();

        // when
        var response = post(
                transferBody(referenceId, line(source, "DEBIT", "0"), line(dest, "CREDIT", "0")), ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(countJournals(referenceId)).isZero();
    }

    // Tai khoan nguon con co event cua lan nap tien: loc them theo referenceId cua journal can kiem tra
    private static ConsumerRecord<String, String> eventOf(
            KafkaTestConsumer consumer, String accountNo, String referenceId) {
        return consumer.getRecord(
                consumerRecord -> accountNo.equals(consumerRecord.key())
                        && consumerRecord.value().contains(referenceId),
                EVENT_TIMEOUT);
    }

    private <T> ResponseEntity<T> post(String body, Class<T> responseType) {
        return client.post()
                .uri(POSTINGS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(responseType);
    }

    private String givenFundedAccount(String amount) {
        var accountNo = givenAccount("ACTIVE");
        var body =
                """
                {"referenceType":"DEPOSIT","referenceId":"%s","description":"Nap tien IT",
                 "lines":[%s,%s]}""".formatted(newReferenceId(), line(CASH_VAULT, "DEBIT", amount), line(accountNo, "CREDIT", amount));
        assertThat(post(body, PostingResponse.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return accountNo;
    }

    private String givenAccount(String status) {
        var accountNo = randomAccountNo();
        jdbcTemplate.update("""
                INSERT INTO ACCOUNT (ACCOUNT_NO, CIF, ACCOUNT_TYPE, CURRENCY, BALANCE, AVAILABLE_BALANCE, STATUS,
                                     VERSION, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, 'CURRENT', 'VND', 0, 0, ?, 0, SYSTIMESTAMP, SYSTIMESTAMP)""", accountNo, randomCif(), status);
        return accountNo;
    }

    private static String transferBody(String referenceId, String... lines) {
        return """
                {"referenceType":"TRANSFER","referenceId":"%s","description":"Chuyen tien IT","lines":[%s]}""".formatted(referenceId, String.join(",", lines));
    }

    private static String line(String accountNo, String direction, String amount) {
        return """
                {"accountNo":"%s","direction":"%s","amount":%s}""".formatted(accountNo, direction, amount);
    }

    private BigDecimal balanceOf(String accountNo) {
        return jdbcTemplate.queryForObject(
                "SELECT BALANCE FROM ACCOUNT WHERE ACCOUNT_NO = ?", BigDecimal.class, accountNo);
    }

    private BigDecimal availableBalanceOf(String accountNo) {
        return jdbcTemplate.queryForObject(
                "SELECT AVAILABLE_BALANCE FROM ACCOUNT WHERE ACCOUNT_NO = ?", BigDecimal.class, accountNo);
    }

    private BigDecimal lastBalanceAfter(String accountNo) {
        return jdbcTemplate.queryForObject(
                "SELECT BALANCE_AFTER FROM LEDGER_ENTRY WHERE ACCOUNT_NO = ? ORDER BY ENTRY_ID DESC FETCH FIRST 1 ROW ONLY",
                BigDecimal.class,
                accountNo);
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

    private static String newReferenceId() {
        return UUID.randomUUID().toString();
    }

    // Container Oracle co the duoc dung lai giua cac lan chay: moi test tu sinh du lieu rieng.
    // Tien to 5000 de khong trung so tai khoan sinh tu sequence (1000...) va cua test khac (2000..., 3000..., 4000...)
    private static String randomAccountNo() {
        return "5000%08d".formatted(ThreadLocalRandom.current().nextLong(1, 100_000_000L));
    }

    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }
}
