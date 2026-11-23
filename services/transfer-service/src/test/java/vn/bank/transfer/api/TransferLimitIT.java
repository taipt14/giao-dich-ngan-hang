package vn.bank.transfer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
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
import vn.bank.common.error.ErrorResponse;
import vn.bank.common.test.AbstractIntegrationTest;
import vn.bank.transfer.application.OtpGenerator;
import vn.bank.transfer.domain.BankZone;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.infrastructure.redis.RedisKeys;
import vn.bank.transfer.support.FakeAccountService;

/**
 * Han muc STANDARD (100 trieu/lan, 500 trieu/ngay, bang TRANSFER_LIMIT_CONFIG) qua API tao lenh va xac thuc OTP, voi
 * Oracle, Redis that. Moi test dung tai khoan nguon rieng nen han muc ngay khong anh huong lan nhau.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TransferLimitIT extends AbstractIntegrationTest {

    private static final String TRANSFERS_PATH = "/api/v1/transfers";
    private static final String OTP = "583016";
    private static final String HUNDRED_MILLION = "100000000";
    private static final int CONCURRENT_TRANSFERS = 10;

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

    TransferLimitIT(
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
        ACCOUNT_SERVICE.givenAccount(sourceAccount, cif, "ACTIVE");
        ACCOUNT_SERVICE.givenAccount(destAccount, randomCif(), "ACTIVE");
        when(otpGenerator.generate()).thenReturn(OTP);
    }

    @Test
    void should_return422PerTransactionLimitExceeded_when_amountIs100000001() {
        // when
        var response = create("100000001", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "PER_TRANSACTION_LIMIT_EXCEEDED");
        assertThat(countTransfers()).isZero();
    }

    @Test
    void should_return422DailyLimitExceeded_when_450MillionTransferredAnd60MillionMore() {
        // given
        givenTransferredToday450Million();

        // when
        var response = create("60000000", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "DAILY_LIMIT_EXCEEDED");
        assertThat(usedToday()).isEqualTo("45000000000");
    }

    @Test
    void should_recomputeFromTransferTable_when_redisLostDailyUsage() {
        // given: lenh FAILED khong tinh vao han muc khi tinh lai
        createAndFailAtPosting(HUNDRED_MILLION);
        givenTransferredToday450Million();
        redisTemplate.delete(dailyLimitKey());

        // when
        var over = create("60000000", ErrorResponse.class);
        var within = createTransfer("50000000");
        var confirmed = confirm(within, TransferResponse.class);

        // then
        assertError(over, HttpStatus.UNPROCESSABLE_CONTENT, "DAILY_LIMIT_EXCEEDED");
        assertThat(confirmed.getBody().status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(usedToday()).isEqualTo("50000000000");
    }

    @Test
    void should_failTransferWithDailyLimitExceeded_when_limitUsedUpBeforeOtpConfirmed() {
        // given: lenh 60 trieu tao luc moi chuyen 400 trieu, sau do chuyen them 100 trieu
        for (var i = 0; i < 4; i++) {
            createAndConfirm(HUNDRED_MILLION);
        }
        var late = createTransfer("60000000");
        createAndConfirm(HUNDRED_MILLION);

        // when
        var response = confirm(late, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "DAILY_LIMIT_EXCEEDED");
        var row = jdbcTemplate.queryForMap("SELECT STATUS, FAILURE_CODE FROM TRANSFER WHERE TRANSFER_ID = ?", late);
        assertThat(row).containsEntry("STATUS", "FAILED").containsEntry("FAILURE_CODE", "DAILY_LIMIT_EXCEEDED");
        assertThat(usedToday()).isEqualTo("50000000000");
    }

    @Test
    void should_letAtMostFiveTransfersPassReservation_when_tenConcurrentTransfersOf100Million() throws Exception {
        // given: 10 lenh deu tao duoc (chua giu cho luc tao lenh)
        var transferIds = new ArrayList<String>();
        for (var i = 0; i < CONCURRENT_TRANSFERS; i++) {
            transferIds.add(createTransfer(HUNDRED_MILLION));
        }
        var start = new CountDownLatch(1);
        var tasks = new ArrayList<Callable<ResponseEntity<String>>>();
        for (var transferId : transferIds) {
            tasks.add(() -> {
                start.await();
                return confirm(transferId, String.class);
            });
        }

        // when
        var responses = runConcurrently(tasks, start);

        // then
        var statuses = responses.stream().map(ResponseEntity::getStatusCode).toList();
        assertThat(statuses).filteredOn(HttpStatus.OK::equals).hasSize(5);
        assertThat(responses)
                .filteredOn(response -> response.getStatusCode() == HttpStatus.UNPROCESSABLE_CONTENT)
                .hasSize(5)
                .allSatisfy(response -> assertThat(response.getBody()).contains("DAILY_LIMIT_EXCEEDED"));
        assertThat(countTransfersIn("SUCCESS")).isEqualTo(5);
        assertThat(countTransfersIn("FAILED")).isEqualTo(5);
        assertThat(usedToday()).isEqualTo("50000000000");
    }

    @Test
    void should_releaseDailyLimit_when_transferFailed() {
        // given: da chuyen 400 trieu, lenh thu 5 va lenh thu 6 (100 trieu) deu tao duoc vi chua giu cho
        for (var i = 0; i < 4; i++) {
            createAndConfirm(HUNDRED_MILLION);
        }
        var retried = createTransfer(HUNDRED_MILLION);

        // when: lenh thu 5 giu cho roi posting loi nghiep vu, lenh FAILED va hoan han muc (BANK-045)
        var failed = createAndFailAtPosting(HUNDRED_MILLION);

        // then: phan da hoan du cho lenh thu 6, neu khong hoan thi lenh thu 6 vuot 500 trieu
        assertThat(failed.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(usedToday()).isEqualTo("40000000000");
        assertThat(confirm(retried, TransferResponse.class).getBody().status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(usedToday()).isEqualTo("50000000000");
    }

    private void givenTransferredToday450Million() {
        for (var i = 0; i < 4; i++) {
            createAndConfirm(HUNDRED_MILLION);
        }
        createAndConfirm("50000000");
    }

    // OTP dung, han muc da giu, nhung account-service tu choi posting: lenh FAILED va han muc duoc hoan (BANK-045)
    private TransferResponse createAndFailAtPosting(String amount) {
        var transferId = createTransfer(amount);
        ACCOUNT_SERVICE.givenPostingRejected(transferId, "INSUFFICIENT_BALANCE");
        var response = confirm(transferId, TransferResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private String createAndConfirm(String amount) {
        var transferId = createTransfer(amount);
        var response = confirm(transferId, TransferResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return transferId;
    }

    private String createTransfer(String amount) {
        var response = create(amount, TransferResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().transferId();
    }

    private <T> ResponseEntity<T> create(String amount, Class<T> responseType) {
        var body = """
                {"sourceAccount":"%s","destAccount":"%s","amount":%s,"description":"Chuyen tien"}""".formatted(sourceAccount, destAccount, amount);
        return client.post()
                .uri(TRANSFERS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.set("X-Roles", "CUSTOMER");
                    headers.set("X-Cif", cif);
                    headers.set("Idempotency-Key", UUID.randomUUID().toString());
                })
                .body(body)
                .retrieve()
                .toEntity(responseType);
    }

    private <T> ResponseEntity<T> confirm(String transferId, Class<T> responseType) {
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
                .toEntity(responseType);
    }

    private static List<ResponseEntity<String>> runConcurrently(
            List<Callable<ResponseEntity<String>>> tasks, CountDownLatch start) throws Exception {
        try (var executor = Executors.newFixedThreadPool(tasks.size())) {
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            var responses = new ArrayList<ResponseEntity<String>>();
            for (Future<ResponseEntity<String>> future : futures) {
                responses.add(future.get());
            }
            return responses;
        }
    }

    // Tong da giu trong ngay, don vi 1/100 dong
    private String usedToday() {
        return redisTemplate.opsForValue().get(dailyLimitKey());
    }

    private String dailyLimitKey() {
        return RedisKeys.dailyLimit(sourceAccount, LocalDate.now(clock.withZone(BankZone.ZONE)));
    }

    private int countTransfers() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TRANSFER WHERE CIF = ?", Integer.class, cif);
    }

    private int countTransfersIn(String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM TRANSFER WHERE CIF = ? AND STATUS = ?", Integer.class, cif, status);
    }

    private static void assertError(ResponseEntity<ErrorResponse> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(code);
    }

    private static String randomAccountNo() {
        return "1000%08d".formatted(ThreadLocalRandom.current().nextLong(1, 100_000_000L));
    }

    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }
}
