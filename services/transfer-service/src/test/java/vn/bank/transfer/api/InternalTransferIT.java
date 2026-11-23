package vn.bank.transfer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import vn.bank.common.events.EventJson;
import vn.bank.common.events.Topics;
import vn.bank.common.test.AbstractIntegrationTest;
import vn.bank.transfer.application.OtpGenerator;
import vn.bank.transfer.domain.BankZone;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.infrastructure.redis.RedisKeys;
import vn.bank.transfer.support.FakeAccountService;

/**
 * Thuc hien lenh noi bo sau khi xac thuc OTP (BANK-045) qua API, voi Oracle, Redis that va account-service gia mo phong
 * posting idempotent theo transferId (so du, journal, tu choi nghiep vu, loi ky thuat). Read-timeout cua client goi
 * account-service la 5 giay (spring.http.clients.read-timeout).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class InternalTransferIT extends AbstractIntegrationTest {

    private static final String TRANSFERS_PATH = "/api/v1/transfers";
    private static final String OTP = "583016";
    private static final String AMOUNT = "300000";
    // Lau hon read-timeout 5 giay: client timeout du account-service da ghi journal
    private static final Duration SLOWER_THAN_READ_TIMEOUT = Duration.ofSeconds(6);
    // Lan goi dau + 3 lan retry (bank.transfer.posting.max-retries)
    private static final int MAX_POSTING_ATTEMPTS = 4;
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(60);
    // Kafka tam dung trong khoang nay: nhieu chu ky publisher (1 giay) ma event van NEW
    private static final Duration KAFKA_DOWN_PERIOD = Duration.ofSeconds(5);

    private static final FakeAccountService ACCOUNT_SERVICE = FakeAccountService.start();

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    @MockitoBean
    private OtpGenerator otpGenerator;

    @LocalServerPort
    private int port;

    private RestClient client;
    private String cif;
    private String sourceAccount;
    private String destAccount;

    InternalTransferIT(
            @Autowired JdbcTemplate jdbcTemplate,
            @Autowired StringRedisTemplate redisTemplate,
            @Autowired Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.clock = clock;
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
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
                .build();
        cif = randomCif();
        sourceAccount = randomAccountNo();
        destAccount = randomAccountNo();
        ACCOUNT_SERVICE.givenAccount(sourceAccount, cif, "ACTIVE", "1000000");
        ACCOUNT_SERVICE.givenAccount(destAccount, randomCif(), "ACTIVE", "0");
        when(otpGenerator.generate()).thenReturn(OTP);
    }

    @Test
    void should_debitSourceCreditDestAndSucceed_when_aWith1000000Transfers300000ToB() {
        // given
        var transferId = createTransfer();

        // when
        var response = confirm(transferId);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(response.getBody().failureCode()).isNull();
        assertThat(ACCOUNT_SERVICE.balanceOf(sourceAccount)).isEqualByComparingTo("700000");
        assertThat(ACCOUNT_SERVICE.balanceOf(destAccount)).isEqualByComparingTo("300000");
        assertThat(ACCOUNT_SERVICE.transferJournalLines(transferId))
                .containsExactly(sourceAccount + " DEBIT 300000.00", destAccount + " CREDIT 300000.00");
        assertThat(statusOf(transferId)).isEqualTo("SUCCESS");
        assertThat(historyStatusesOf(transferId)).containsExactly("INITIATED", "OTP_PENDING", "PROCESSING", "SUCCESS");
        assertThat(usedToday()).isEqualTo("30000000");
    }

    @Test
    void should_failWithInsufficientBalanceAndReleaseLimit_when_balanceNotEnoughAtPosting() {
        // given: du so du luc tao lenh, sau do so du giam truoc khi xac thuc OTP
        var transferId = createTransfer();
        ACCOUNT_SERVICE.givenAccount(sourceAccount, cif, "ACTIVE", "100000");

        // when
        var response = confirm(transferId);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(TransferStatus.FAILED);
        assertThat(response.getBody().failureCode()).isEqualTo("INSUFFICIENT_BALANCE");
        var row =
                jdbcTemplate.queryForMap("SELECT STATUS, FAILURE_CODE FROM TRANSFER WHERE TRANSFER_ID = ?", transferId);
        assertThat(row).containsEntry("STATUS", "FAILED").containsEntry("FAILURE_CODE", "INSUFFICIENT_BALANCE");
        assertThat(usedToday()).isEqualTo("0");
        assertThat(ACCOUNT_SERVICE.balanceOf(sourceAccount)).isEqualByComparingTo("100000");
        assertThat(ACCOUNT_SERVICE.balanceOf(destAccount)).isEqualByComparingTo("0");
    }

    @Test
    void should_failWithAccountNotActive_when_destAccountLockedBeforePosting() {
        // given
        var transferId = createTransfer();
        ACCOUNT_SERVICE.givenAccount(destAccount, randomCif(), "LOCKED", "0");

        // when
        var response = confirm(transferId);

        // then
        assertThat(response.getBody().status()).isEqualTo(TransferStatus.FAILED);
        assertThat(response.getBody().failureCode()).isEqualTo("ACCOUNT_NOT_ACTIVE");
        assertThat(usedToday()).isEqualTo("0");
        assertThat(ACCOUNT_SERVICE.balanceOf(sourceAccount)).isEqualByComparingTo("1000000");
    }

    @Test
    void should_retryWithoutDebitingTwice_when_accountServiceTimesOutButPostingRecorded() {
        // given: lan goi dau da ghi journal nhung response den sau read-timeout
        var transferId = createTransfer();
        ACCOUNT_SERVICE.givenPostingResponseDelayedOnce(transferId, SLOWER_THAN_READ_TIMEOUT);

        // when
        var response = confirm(transferId);

        // then: lan retry gap journal da co, account-service tra lai journal cu, khong tru tien lan 2
        assertThat(response.getBody().status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(ACCOUNT_SERVICE.postingRequestCount(transferId)).isEqualTo(2);
        assertThat(ACCOUNT_SERVICE.balanceOf(sourceAccount)).isEqualByComparingTo("700000");
        assertThat(ACCOUNT_SERVICE.balanceOf(destAccount)).isEqualByComparingTo("300000");
        assertThat(statusOf(transferId)).isEqualTo("SUCCESS");
    }

    @Test
    void should_markUnknownAndKeepLimit_when_accountServiceKeepsFailingAfterThreeRetries() {
        // given
        var transferId = createTransfer();
        ACCOUNT_SERVICE.givenPostingUnavailable(transferId);

        // when
        var response = confirm(transferId);

        // then: khong FAILED vi chua chac tien chua bi tru; han muc van giu
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(TransferStatus.UNKNOWN);
        assertThat(ACCOUNT_SERVICE.postingRequestCount(transferId)).isEqualTo(MAX_POSTING_ATTEMPTS);
        assertThat(statusOf(transferId)).isEqualTo("UNKNOWN");
        assertThat(historyStatusesOf(transferId)).containsExactly("INITIATED", "OTP_PENDING", "PROCESSING", "UNKNOWN");
        assertThat(usedToday()).isEqualTo("30000000");
    }

    @Test
    void should_publishTransferCompleted_when_internalTransferSucceeded() {
        // given
        var transferId = createTransfer();

        try (var consumer = createKafkaTestConsumer(Topics.TRANSFER_EVENTS)) {
            // when
            confirm(transferId);

            // then
            var event = EventJson.mapper()
                    .readTree(consumer.getRecordByKey(transferId, EVENT_TIMEOUT).value());
            assertThat(event.get("eventType").asString()).isEqualTo("TransferCompleted");
            assertThat(event.get("source").asString()).isEqualTo("transfer-service");
            var payload = event.get("payload");
            assertThat(payload.get("transferId").asString()).isEqualTo(transferId);
            assertThat(payload.get("type").asString()).isEqualTo("INTERNAL");
            assertThat(payload.get("cif").asString()).isEqualTo(cif);
            assertThat(payload.get("sourceAccount").asString()).isEqualTo(sourceAccount);
            assertThat(payload.get("destAccount").asString()).isEqualTo(destAccount);
            assertThat(payload.get("amount").decimalValue()).isEqualByComparingTo(AMOUNT);
            assertThat(payload.get("fee").decimalValue()).isEqualByComparingTo("0");
            assertThat(payload.get("completedAt").asString()).isNotBlank();
        }
    }

    @Test
    void should_publishTransferFailedWithFailureCode_when_postingRejected() {
        // given
        var transferId = createTransfer();
        ACCOUNT_SERVICE.givenAccount(sourceAccount, cif, "ACTIVE", "100000");

        try (var consumer = createKafkaTestConsumer(Topics.TRANSFER_EVENTS)) {
            // when
            confirm(transferId);

            // then
            var event = EventJson.mapper()
                    .readTree(consumer.getRecordByKey(transferId, EVENT_TIMEOUT).value());
            assertThat(event.get("eventType").asString()).isEqualTo("TransferFailed");
            var payload = event.get("payload");
            assertThat(payload.get("transferId").asString()).isEqualTo(transferId);
            assertThat(payload.get("status").asString()).isEqualTo("FAILED");
            assertThat(payload.get("failureCode").asString()).isEqualTo("INSUFFICIENT_BALANCE");
            assertThat(payload.get("amount").decimalValue()).isEqualByComparingTo(AMOUNT);
        }
    }

    @Test
    void should_notPublishAnyTransferEvent_when_transferUnknown() {
        // given
        var transferId = createTransfer();
        ACCOUNT_SERVICE.givenPostingUnavailable(transferId);

        // when
        confirm(transferId);

        // then: UNKNOWN chua phai ket qua cuoi, khong co dong outbox nao cho lenh
        assertThat(outboxStatusOf(transferId)).isNull();
    }

    /**
     * Tam dung container Kafka cua test (docker pause): broker khong tra loi, nhu Kafka chet. Giao dich van chay vi
     * khong goi Kafka trong luong request; event nam NEW trong outbox va duoc gui khi Kafka chay lai.
     */
    @Test
    void should_publishTransferCompletedAfterKafkaBack_when_kafkaDownDuringTransfer() {
        // given
        pauseKafka();
        String transferId;
        try {
            // when
            transferId = createTransfer();
            var response = confirm(transferId);

            // then
            assertThat(response.getBody().status()).isEqualTo(TransferStatus.SUCCESS);
            await().during(KAFKA_DOWN_PERIOD)
                    .atMost(KAFKA_DOWN_PERIOD.plus(EVENT_TIMEOUT))
                    .until(() -> "NEW".equals(outboxStatusOf(transferId)));
        } finally {
            unpauseKafka();
        }
        await().atMost(EVENT_TIMEOUT).until(() -> "SENT".equals(outboxStatusOf(transferId)));
        try (var consumer = createKafkaTestConsumer(Topics.TRANSFER_EVENTS)) {
            var event = EventJson.mapper()
                    .readTree(consumer.getRecordByKey(transferId, EVENT_TIMEOUT).value());
            assertThat(event.get("eventType").asString()).isEqualTo("TransferCompleted");
        }
    }

    private static void pauseKafka() {
        KAFKA.getDockerClient().pauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    private static void unpauseKafka() {
        KAFKA.getDockerClient().unpauseContainerCmd(KAFKA.getContainerId()).exec();
    }

    private String outboxStatusOf(String transferId) {
        var statuses = jdbcTemplate.queryForList(
                "SELECT STATUS FROM OUTBOX_EVENT WHERE AGGREGATE_ID = ?", String.class, transferId);
        return statuses.isEmpty() ? null : statuses.getFirst();
    }

    private String createTransfer() {
        var body = """
                {"sourceAccount":"%s","destAccount":"%s","amount":%s,"description":"Chuyen tien"}""".formatted(sourceAccount, destAccount, AMOUNT);
        var response = client.post()
                .uri(TRANSFERS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.set("X-Roles", "CUSTOMER");
                    headers.set("X-Cif", cif);
                    headers.set("Idempotency-Key", UUID.randomUUID().toString());
                })
                .body(body)
                .retrieve()
                .toEntity(TransferResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().transferId();
    }

    private ResponseEntity<TransferResponse> confirm(String transferId) {
        return client.post()
                .uri(TRANSFERS_PATH + "/{id}/confirm", transferId)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.set("X-Roles", "CUSTOMER");
                    headers.set("X-Cif", cif);
                })
                .body("""
                        {"otp":"%s"}""".formatted(OTP))
                .retrieve()
                .toEntity(TransferResponse.class);
    }

    private String statusOf(String transferId) {
        return jdbcTemplate.queryForObject(
                "SELECT STATUS FROM TRANSFER WHERE TRANSFER_ID = ?", String.class, transferId);
    }

    private List<String> historyStatusesOf(String transferId) {
        return jdbcTemplate.queryForList(
                "SELECT STATUS FROM TRANSFER_STATUS_HISTORY WHERE TRANSFER_ID = ? ORDER BY TRANSFER_STATUS_HISTORY_ID",
                String.class,
                transferId);
    }

    // Tong da giu trong ngay cua tai khoan nguon, don vi 1/100 dong
    private String usedToday() {
        return redisTemplate
                .opsForValue()
                .get(RedisKeys.dailyLimit(sourceAccount, LocalDate.now(clock.withZone(BankZone.ZONE))));
    }

    private static String randomAccountNo() {
        return "1000%08d".formatted(ThreadLocalRandom.current().nextLong(1, 100_000_000L));
    }

    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }
}
