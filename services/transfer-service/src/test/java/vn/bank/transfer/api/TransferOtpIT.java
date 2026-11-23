package vn.bank.transfer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import vn.bank.transfer.domain.OtpHash;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.support.FakeAccountService;

/**
 * Luong OTP qua API ({@code /confirm}, {@code /otp/resend}) voi Oracle, Redis that. OtpGenerator duoc thay bang mock de
 * test biet OTP ma khong can doc log; moi truong test khong bat {@code dev-log-enabled} nen giong moi truong khac dev.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class TransferOtpIT extends AbstractIntegrationTest {

    private static final String TRANSFERS_PATH = "/api/v1/transfers";
    private static final String OTP = "583016";
    private static final String RESENT_OTP = "264097";
    private static final String WRONG_OTP = "111111";
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_RESEND = 3;
    private static final Duration OTP_TTL = Duration.ofMinutes(3);

    private static final FakeAccountService ACCOUNT_SERVICE = FakeAccountService.start();

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    @MockitoBean
    private OtpGenerator otpGenerator;

    @LocalServerPort
    private int port;

    private RestClient client;
    private String cif;
    private String sourceAccount;
    private String destAccount;

    TransferOtpIT(@Autowired JdbcTemplate jdbcTemplate, @Autowired StringRedisTemplate redisTemplate) {
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
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
                .build();
        cif = randomCif();
        sourceAccount = randomAccountNo();
        destAccount = randomAccountNo();
        ACCOUNT_SERVICE.givenAccount(sourceAccount, cif, "ACTIVE");
        ACCOUNT_SERVICE.givenAccount(destAccount, randomCif(), "ACTIVE");
        when(otpGenerator.generate()).thenReturn(OTP, RESENT_OTP);
    }

    @Test
    void should_return201OtpPendingAndStoreOnlyOtpHashFor3Minutes_when_transferCreated() {
        // when
        var response = create(UUID.randomUUID().toString(), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"status\":\"OTP_PENDING\"").doesNotContain(OTP);
        var transferId = transferIdOf(cif);
        assertThat(redisTemplate.opsForValue().get("otp:" + transferId)).isEqualTo(OtpHash.of(transferId, OTP));
        assertThat(Duration.ofSeconds(redisTemplate.getExpire("otp:" + transferId)))
                .isBetween(OTP_TTL.minusSeconds(10), OTP_TTL);
        assertThat(historyStatusesOf(transferId)).containsExactly("INITIATED", "OTP_PENDING");
    }

    @Test
    void should_return200WithSameTransferWithoutNewOtp_when_createReplayedWithSameKey() {
        // given
        var key = UUID.randomUUID().toString();
        create(key, String.class);

        // when
        var replay = create(key, TransferResponse.class);

        // then: OTP cua lenh van la OTP dau tien
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().status()).isEqualTo(TransferStatus.OTP_PENDING);
        verify(otpGenerator, times(1)).generate();
        var transferId = replay.getBody().transferId();
        assertThat(redisTemplate.opsForValue().get("otp:" + transferId)).isEqualTo(OtpHash.of(transferId, OTP));
    }

    @Test
    void should_return200ProcessingAndDeleteOtp_when_correctOtpConfirmed() {
        // given
        var transferId = createTransfer();

        // when
        var response = confirm(transferId, cif, OTP, TransferResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(TransferStatus.PROCESSING);
        assertThat(statusOf(transferId)).isEqualTo("PROCESSING");
        assertThat(historyStatusesOf(transferId)).containsExactly("INITIATED", "OTP_PENDING", "PROCESSING");
        assertThat(redisTemplate.hasKey("otp:" + transferId)).isFalse();
    }

    @Test
    void should_return422OtpExpired_when_otpExpired() {
        // given: OTP het TTL
        var transferId = createTransfer();
        redisTemplate.expire("otp:" + transferId, Duration.ofMillis(1));
        await().atMost(Duration.ofSeconds(2)).until(() -> !redisTemplate.hasKey("otp:" + transferId));

        // when
        var response = confirm(transferId, cif, OTP, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "OTP_EXPIRED");
        assertThat(statusOf(transferId)).isEqualTo("OTP_PENDING");
    }

    @Test
    void should_return422_when_verifiedOtpReused() {
        // given
        var transferId = createTransfer();
        confirm(transferId, cif, OTP, TransferResponse.class);

        // when
        var response = confirm(transferId, cif, OTP, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "TRANSFER_NOT_AWAITING_OTP");
        assertThat(historyStatusesOf(transferId)).containsExactly("INITIATED", "OTP_PENDING", "PROCESSING");
    }

    @Test
    void should_return422OtpInvalidAndKeepOtpPending_when_wrongOtp() {
        // given
        var transferId = createTransfer();

        // when
        var response = confirm(transferId, cif, WRONG_OTP, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "OTP_INVALID");
        assertThat(statusOf(transferId)).isEqualTo("OTP_PENDING");
        assertThat(redisTemplate.opsForValue().get("otp:fail:" + transferId)).isEqualTo("1");
    }

    @Test
    void should_failTransferAndRejectCorrectOtp_when_wrongOtpFiveTimes() {
        // given
        var transferId = createTransfer();
        for (var i = 1; i < MAX_ATTEMPTS; i++) {
            assertError(
                    confirm(transferId, cif, WRONG_OTP, ErrorResponse.class),
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "OTP_INVALID");
        }

        // when
        var fifth = confirm(transferId, cif, WRONG_OTP, ErrorResponse.class);
        var correctAfterwards = confirm(transferId, cif, OTP, ErrorResponse.class);

        // then
        assertError(fifth, HttpStatus.UNPROCESSABLE_CONTENT, "OTP_MAX_ATTEMPTS");
        var row =
                jdbcTemplate.queryForMap("SELECT STATUS, FAILURE_CODE FROM TRANSFER WHERE TRANSFER_ID = ?", transferId);
        assertThat(row).containsEntry("STATUS", "FAILED").containsEntry("FAILURE_CODE", "OTP_MAX_ATTEMPTS");
        assertError(correctAfterwards, HttpStatus.UNPROCESSABLE_CONTENT, "TRANSFER_NOT_AWAITING_OTP");
        assertThat(statusOf(transferId)).isEqualTo("FAILED");
    }

    @Test
    void should_return400AndNotCountAttempt_when_otpNotSixDigits() {
        // given
        var transferId = createTransfer();

        // when
        var response = confirm(transferId, cif, "12ab", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(redisTemplate.hasKey("otp:fail:" + transferId)).isFalse();
    }

    @Test
    void should_return404TransferNotFound_when_otherCustomerConfirmsOrResends() {
        // given
        var transferId = createTransfer();
        var otherCif = randomCif();

        // when
        var confirmed = confirm(transferId, otherCif, OTP, ErrorResponse.class);
        var resent = resend(transferId, otherCif, ErrorResponse.class);

        // then: OTP cua chu lenh khong bi dung, khong bi thay
        assertError(confirmed, HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND");
        assertError(resent, HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND");
        assertThat(redisTemplate.opsForValue().get("otp:" + transferId)).isEqualTo(OtpHash.of(transferId, OTP));
    }

    @Test
    void should_return404TransferNotFound_when_transferDoesNotExist() {
        // when
        var response = confirm(UUID.randomUUID().toString(), cif, OTP, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND");
    }

    @Test
    void should_return429OtpResendTooSoon_when_resendWithin30Seconds() {
        // given
        var transferId = createTransfer();

        // when
        var response = resend(transferId, cif, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.TOO_MANY_REQUESTS, "OTP_RESEND_TOO_SOON");
        assertThat(redisTemplate.opsForValue().get("otp:" + transferId)).isEqualTo(OtpHash.of(transferId, OTP));
    }

    @Test
    void should_acceptOnlyNewOtp_when_otpResentAfter30Seconds() {
        // given: gia lap da qua 30 giay tu lan gui truoc
        var transferId = createTransfer();
        passResendInterval(transferId);

        // when
        var resent = resend(transferId, cif, TransferResponse.class);

        // then
        assertThat(resent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resent.getBody().status()).isEqualTo(TransferStatus.OTP_PENDING);
        assertError(
                confirm(transferId, cif, OTP, ErrorResponse.class), HttpStatus.UNPROCESSABLE_CONTENT, "OTP_INVALID");
        assertThat(confirm(transferId, cif, RESENT_OTP, TransferResponse.class)
                        .getBody()
                        .status())
                .isEqualTo(TransferStatus.PROCESSING);
    }

    @Test
    void should_return422OtpResendLimitExceeded_when_resendingFourthTime() {
        // given
        var transferId = createTransfer();
        for (var i = 0; i < MAX_RESEND; i++) {
            passResendInterval(transferId);
            assertThat(resend(transferId, cif, TransferResponse.class).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }
        passResendInterval(transferId);

        // when
        var response = resend(transferId, cif, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "OTP_RESEND_LIMIT_EXCEEDED");
    }

    @Test
    void should_neverLogPlainOtp_when_notDevEnvironment(CapturedOutput output) {
        // given: moi truong test khong bat bank.transfer.otp.dev-log-enabled
        var transferId = createTransfer();
        confirm(transferId, cif, WRONG_OTP, ErrorResponse.class);
        passResendInterval(transferId);
        resend(transferId, cif, TransferResponse.class);

        // when
        confirm(transferId, cif, RESENT_OTP, TransferResponse.class);

        // then
        assertThat(output).contains("OTP sent for transfer " + transferId).doesNotContain("[DEV]");
        assertThat(output.getAll())
                .doesNotContain(OTP)
                .doesNotContain(RESENT_OTP)
                .doesNotContain(WRONG_OTP);
    }

    private String createTransfer() {
        var response = create(UUID.randomUUID().toString(), TransferResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().transferId();
    }

    private <T> ResponseEntity<T> create(String idempotencyKey, Class<T> responseType) {
        var body = """
                {"sourceAccount":"%s","destAccount":"%s","amount":250000,"description":"Chuyen tien"}""".formatted(sourceAccount, destAccount);
        return client.post()
                .uri(TRANSFERS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.set("X-Roles", "CUSTOMER");
                    headers.set("X-Cif", cif);
                    headers.set("Idempotency-Key", idempotencyKey);
                })
                .body(body)
                .retrieve()
                .toEntity(responseType);
    }

    private <T> ResponseEntity<T> confirm(String transferId, String customerCif, String otp, Class<T> responseType) {
        return client.post()
                .uri(TRANSFERS_PATH + "/{id}/confirm", transferId)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> {
                    headers.set("X-Roles", "CUSTOMER");
                    headers.set("X-Cif", customerCif);
                })
                .body("""
                        {"otp":"%s"}""".formatted(otp))
                .retrieve()
                .toEntity(responseType);
    }

    private <T> ResponseEntity<T> resend(String transferId, String customerCif, Class<T> responseType) {
        return client.post()
                .uri(TRANSFERS_PATH + "/{id}/otp/resend", transferId)
                .headers(headers -> {
                    headers.set("X-Roles", "CUSTOMER");
                    headers.set("X-Cif", customerCif);
                })
                .retrieve()
                .toEntity(responseType);
    }

    // Khoang cach gui lai do TTL key otp:cooldown giu: xoa key thay cho cho 30 giay
    private void passResendInterval(String transferId) {
        redisTemplate.delete("otp:cooldown:" + transferId);
    }

    private String transferIdOf(String customerCif) {
        return jdbcTemplate.queryForObject("SELECT TRANSFER_ID FROM TRANSFER WHERE CIF = ?", String.class, customerCif);
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
