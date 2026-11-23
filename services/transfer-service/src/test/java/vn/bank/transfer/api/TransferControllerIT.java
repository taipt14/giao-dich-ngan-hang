package vn.bank.transfer.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import vn.bank.common.error.ErrorResponse;
import vn.bank.common.test.AbstractIntegrationTest;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.domain.TransferType;
import vn.bank.transfer.support.FakeAccountService;

/**
 * {@code POST /api/v1/transfers} voi Oracle, Redis that va account-service gia. Container co the duoc dung lai giua cac
 * lan chay nen moi test dung CIF, so tai khoan va Idempotency-Key rieng.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TransferControllerIT extends AbstractIntegrationTest {

    private static final String TRANSFERS_PATH = "/api/v1/transfers";
    private static final String ROLES_HEADER = "X-Roles";
    private static final String CIF_HEADER = "X-Cif";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String CUSTOMER = "CUSTOMER";
    private static final int CONCURRENT_REQUESTS = 5;
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private static final FakeAccountService ACCOUNT_SERVICE = FakeAccountService.start();

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    @LocalServerPort
    private int port;

    private RestClient client;
    private String cif;
    private String sourceAccount;
    private String destAccount;

    TransferControllerIT(@Autowired JdbcTemplate jdbcTemplate, @Autowired StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @DynamicPropertySource
    static void accountServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("bank.transfer.account-service.base-url", ACCOUNT_SERVICE::uri);
    }

    @AfterAll
    static void stopAccountService() {
        ACCOUNT_SERVICE.close();
    }

    @BeforeEach
    void setUp() {
        // Khong nem exception voi 4xx/5xx de test doc duoc body loi
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
                .build();
        cif = randomCif();
        sourceAccount = randomAccountNo();
        destAccount = randomAccountNo();
        ACCOUNT_SERVICE.givenAccount(sourceAccount, cif, "ACTIVE");
        ACCOUNT_SERVICE.givenAccount(destAccount, randomCif(), "ACTIVE");
    }

    @Test
    void should_return201WithOtpPendingTransferAndExpectedFee_when_transferCreated() {
        // given
        var key = newKey();

        // when
        var response = create(key, body(sourceAccount, destAccount, "1500000"), TransferResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var transfer = response.getBody();
        assertThat(transfer).isNotNull();
        assertThat(transfer.transferId()).isNotBlank();
        assertThat(transfer.type()).isEqualTo(TransferType.INTERNAL);
        assertThat(transfer.status()).isEqualTo(TransferStatus.OTP_PENDING);
        assertThat(transfer.amount()).isEqualTo(new BigDecimal("1500000.00"));
        assertThat(transfer.fee()).isEqualTo(new BigDecimal("0.00"));
        var row = jdbcTemplate.queryForMap(
                "SELECT TRANSFER_ID, STATUS, CIF, IDEMPOTENCY_KEY FROM TRANSFER WHERE CIF = ? AND IDEMPOTENCY_KEY = ?",
                cif,
                key);
        assertThat(row).containsEntry("TRANSFER_ID", transfer.transferId()).containsEntry("STATUS", "OTP_PENDING");
        assertThat(historyStatusesOf(transfer.transferId())).containsExactly("INITIATED", "OTP_PENDING");
    }

    @Test
    void should_storeIdempotencyKeyInRedisFor24Hours_when_transferCreated() {
        // given
        var key = newKey();

        // when
        create(key, body(sourceAccount, destAccount, "1000"), TransferResponse.class);

        // then
        var ttl = redisTemplate.getExpire("idem:" + cif + ":" + key);
        assertThat(Duration.ofSeconds(ttl)).isBetween(IDEMPOTENCY_TTL.minusMinutes(1), IDEMPOTENCY_TTL);
    }

    @Test
    void should_return400IdempotencyKeyRequired_when_headerMissing() {
        // given: body hop le, khong co header Idempotency-Key

        // when
        var response = create(null, body(sourceAccount, destAccount, "1000"), ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        assertThat(countTransfersOf(cif)).isZero();
    }

    @Test
    void should_return400IdempotencyKeyInvalid_when_headerNotUuid() {
        // given
        var key = "not-a-uuid";

        // when
        var response = create(key, body(sourceAccount, destAccount, "1000"), ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID");
    }

    @Test
    void should_createOnlyOneTransfer_when_fiveConcurrentRequestsWithSameKey() throws Exception {
        // given
        var key = newKey();
        var body = body(sourceAccount, destAccount, "200000");
        var start = new CountDownLatch(1);
        var tasks = new ArrayList<Callable<ResponseEntity<String>>>();
        for (var i = 0; i < CONCURRENT_REQUESTS; i++) {
            tasks.add(() -> {
                start.await();
                return create(key, body, String.class);
            });
        }

        // when
        List<ResponseEntity<String>> responses;
        try (var executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            responses = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                responses.add(future.get());
            }
        }

        // then: mot request tao lenh, cac request con lai nhan lenh cu (200) hoac bao dang xu ly (409)
        assertThat(countTransfersOf(cif)).isEqualTo(1);
        var statuses = responses.stream().map(ResponseEntity::getStatusCode).toList();
        assertThat(statuses).filteredOn(HttpStatus.CREATED::equals).hasSize(1);
        assertThat(statuses)
                .allMatch(status ->
                        status == HttpStatus.CREATED || status == HttpStatus.OK || status == HttpStatus.CONFLICT);
        var transferId = transferIdOf(cif, key);
        assertThat(responses)
                .filteredOn(response -> response.getStatusCode().is2xxSuccessful())
                .allSatisfy(response -> assertThat(response.getBody()).contains(transferId));
    }

    @Test
    void should_return200WithSameTransfer_when_sameKeyAndContentResent() {
        // given
        var key = newKey();
        var first = create(key, body(sourceAccount, destAccount, "300000"), TransferResponse.class);

        // when: so tien viet khac scale van la cung noi dung
        var second = create(key, body(sourceAccount, destAccount, "300000.00"), TransferResponse.class);

        // then
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().transferId()).isEqualTo(first.getBody().transferId());
        assertThat(second.getBody().amount()).isEqualTo(first.getBody().amount());
        assertThat(second.getBody().fee()).isEqualTo(first.getBody().fee());
        assertThat(countTransfersOf(cif)).isEqualTo(1);
    }

    @Test
    void should_return422IdempotencyKeyReused_when_sameKeyWithDifferentContent() {
        // given
        var key = newKey();
        create(key, body(sourceAccount, destAccount, "300000"), TransferResponse.class);

        // when
        var response = create(key, body(sourceAccount, destAccount, "400000"), ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED");
        assertThat(countTransfersOf(cif)).isEqualTo(1);
    }

    @Test
    void should_return200WithSameTransfer_when_redisKeyLostAndSameRequestResent() {
        // given
        var key = newKey();
        var first = create(key, body(sourceAccount, destAccount, "300000"), TransferResponse.class);
        redisTemplate.delete("idem:" + cif + ":" + key);

        // when: lop thu hai (UK trong DB) van chong tao trung
        var second = create(key, body(sourceAccount, destAccount, "300000"), TransferResponse.class);

        // then
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().transferId()).isEqualTo(first.getBody().transferId());
        assertThat(countTransfersOf(cif)).isEqualTo(1);
    }

    @Test
    void should_return422SameAccountTransfer_when_destIsSourceAccount() {
        // given
        var key = newKey();

        // when
        var response = create(key, body(sourceAccount, sourceAccount, "1000"), ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "SAME_ACCOUNT_TRANSFER");
        assertThat(countTransfersOf(cif)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1000", "100.001"})
    void should_return400_when_amountNotPositiveOrMoreThanTwoDecimals(String amount) {
        // given
        var key = newKey();

        // when
        var response = create(key, body(sourceAccount, destAccount, amount), ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(countTransfersOf(cif)).isZero();
    }

    @Test
    void should_return403SourceAccountAccessDenied_when_sourceAccountOfOtherCustomer() {
        // given
        var otherCustomersAccount = randomAccountNo();
        ACCOUNT_SERVICE.givenAccount(otherCustomersAccount, randomCif(), "ACTIVE");

        // when
        var response = create(newKey(), body(otherCustomersAccount, destAccount, "1000"), ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "SOURCE_ACCOUNT_ACCESS_DENIED");
        assertThat(countTransfersOf(cif)).isZero();
    }

    @Test
    void should_return422DestAccountNotFound_when_internalDestDoesNotExist() {
        // given
        var unknownAccount = randomAccountNo();

        // when
        var response = create(newKey(), body(sourceAccount, unknownAccount, "1000"), ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "DEST_ACCOUNT_NOT_FOUND");
    }

    @Test
    void should_return422DestAccountNotActiveThenAllowRetrySameKey_when_destLockedThenUnlocked() {
        // given
        var key = newKey();
        ACCOUNT_SERVICE.givenAccount(destAccount, randomCif(), "LOCKED");
        var rejected = create(key, body(sourceAccount, destAccount, "1000"), ErrorResponse.class);
        ACCOUNT_SERVICE.givenAccount(destAccount, randomCif(), "ACTIVE");

        // when: lan tu choi da tra key, gui lai cung key khong bi bao dang xu ly
        var retried = create(key, body(sourceAccount, destAccount, "1000"), TransferResponse.class);

        // then
        assertError(rejected, HttpStatus.UNPROCESSABLE_CONTENT, "DEST_ACCOUNT_NOT_ACTIVE");
        assertThat(retried.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void should_createInterbankTransferWithoutDestLookup_when_destBankCodeGiven() {
        // given: tai khoan dich o ngan hang khac, account-service khong biet
        var body = """
                {"sourceAccount":"%s","destAccount":"0123456789","destBankCode":"970436","amount":500000}""".formatted(sourceAccount);

        // when
        var response = create(newKey(), body, TransferResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().type()).isEqualTo(TransferType.INTERBANK);
        assertThat(response.getBody().destBankCode()).isEqualTo("970436");
    }

    @Test
    void should_return403AccessDenied_when_userIsNotCustomer() {
        // given
        var body = body(sourceAccount, destAccount, "1000");

        // when
        var response = client.post()
                .uri(TRANSFERS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(ROLES_HEADER, "ADMIN")
                .header(IDEMPOTENCY_KEY_HEADER, newKey())
                .body(body)
                .retrieve()
                .toEntity(ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    private <T> ResponseEntity<T> create(String idempotencyKey, String body, Class<T> responseType) {
        return client.post()
                .uri(TRANSFERS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.set(ROLES_HEADER, CUSTOMER);
                    headers.set(CIF_HEADER, cif);
                    if (idempotencyKey != null) {
                        headers.set(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
                    }
                })
                .body(body)
                .retrieve()
                .toEntity(responseType);
    }

    private static String body(String source, String dest, String amount) {
        return """
                {"sourceAccount":"%s","destAccount":"%s","amount":%s,"description":"Chuyen tien"}""".formatted(source, dest, amount);
    }

    private Integer countTransfersOf(String customerCif) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TRANSFER WHERE CIF = ?", Integer.class, customerCif);
    }

    private String transferIdOf(String customerCif, String key) {
        return jdbcTemplate.queryForObject(
                "SELECT TRANSFER_ID FROM TRANSFER WHERE CIF = ? AND IDEMPOTENCY_KEY = ?",
                String.class,
                customerCif,
                key);
    }

    private List<String> historyStatusesOf(String transferId) {
        return jdbcTemplate.queryForList(
                "SELECT STATUS FROM TRANSFER_STATUS_HISTORY WHERE TRANSFER_ID = ? ORDER BY TRANSFER_STATUS_HISTORY_ID",
                String.class,
                transferId);
    }

    private static void assertError(ResponseEntity<ErrorResponse> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(code);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static String randomAccountNo() {
        return "1000%08d".formatted(ThreadLocalRandom.current().nextLong(1, 100_000_000L));
    }

    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }
}
