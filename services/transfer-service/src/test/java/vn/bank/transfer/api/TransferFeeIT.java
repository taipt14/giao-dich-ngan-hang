package vn.bank.transfer.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import vn.bank.transfer.domain.BankZone;
import vn.bank.transfer.domain.TransferType;
import vn.bank.transfer.infrastructure.persistence.FeeScheduleRepository;
import vn.bank.transfer.support.FakeAccountService;

/**
 * Phi tinh luc tao lenh theo bieu phi FEE_SCHEDULE (V4) va kiem tra so du kha dung >= so tien + phi (BANK-044). Bieu
 * phi them trong test luon co ngay hieu luc tu ngay mai va bi xoa sau test, khong anh huong test khac.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TransferFeeIT extends AbstractIntegrationTest {

    private static final String TRANSFERS_PATH = "/api/v1/transfers";
    private static final String NAPAS_BANK_CODE = "970436";
    private static final String DELETE_TEST_SCHEDULE =
            "DELETE FROM FEE_SCHEDULE WHERE TRANSFER_TYPE = 'INTERBANK' AND EFFECTIVE_FROM = ?";

    private static final FakeAccountService ACCOUNT_SERVICE = FakeAccountService.start();

    private final JdbcTemplate jdbcTemplate;
    private final FeeScheduleRepository feeScheduleRepository;
    private final Clock clock;

    @LocalServerPort
    private int port;

    private RestClient client;
    private String cif;
    private String sourceAccount;
    private String destAccount;

    TransferFeeIT(
            @Autowired JdbcTemplate jdbcTemplate,
            @Autowired FeeScheduleRepository feeScheduleRepository,
            @Autowired Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.feeScheduleRepository = feeScheduleRepository;
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
    }

    @AfterEach
    void removeTestSchedule() {
        jdbcTemplate.update(DELETE_TEST_SCHEDULE, Date.valueOf(tomorrow()));
    }

    @ParameterizedTest
    @CsvSource({"499999, 0.00", "500000, 2200.00", "20000000, 4000.00", "100000000, 11000.00"})
    void should_storeInterbankFeeFromSeedSchedule_when_transferCreated(String amount, String expectedFee) {
        // when
        var response = create(interbankBody(amount), TransferResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().fee()).isEqualTo(new BigDecimal(expectedFee));
        assertThat(storedFeeOf(response.getBody().transferId())).isEqualByComparingTo(expectedFee);
    }

    @Test
    void should_storeZeroFee_when_internalTransfer() {
        // when
        var response = create(internalBody("100000000"), TransferResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(storedFeeOf(response.getBody().transferId())).isEqualByComparingTo("0");
    }

    @Test
    void should_roundFeeHalfUpToWholeDong_when_transferCreated() {
        // when: 0,02% x 12.347.500 = 2.469,5
        var response = create(interbankBody("12347500"), TransferResponse.class);

        // then
        assertThat(storedFeeOf(response.getBody().transferId())).isEqualByComparingTo("2470");
    }

    @Test
    void should_useCurrentSchedule_when_newScheduleEffectiveFromTomorrow() {
        // given: bieu phi lien ngan hang moi 0,05% (5.000 - 50.000) tu ngay mai
        var tomorrow = tomorrow();
        jdbcTemplate.update(DELETE_TEST_SCHEDULE, Date.valueOf(tomorrow));
        jdbcTemplate.update("""
                INSERT INTO FEE_SCHEDULE (FEE_SCHEDULE_ID, TRANSFER_TYPE, MIN_AMOUNT, MAX_AMOUNT, FEE_RATE, MIN_FEE,
                                          MAX_FEE, EFFECTIVE_FROM, VERSION)
                VALUES (SEQ_FEE_SCHEDULE.NEXTVAL, 'INTERBANK', 0, NULL, 0.0005, 5000, 50000, ?, 0)""", Date.valueOf(tomorrow));

        // when
        var response = create(interbankBody("20000000"), TransferResponse.class);

        // then: lenh hom nay van dung bieu phi cu; ngay hieu luc doc tu DB dung ngay da ghi
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(storedFeeOf(response.getBody().transferId())).isEqualByComparingTo("4000");
        assertThat(feeScheduleRepository.findByTransferType(TransferType.INTERBANK))
                .extracting("effectiveFrom")
                .contains(tomorrow);
    }

    @Test
    void should_return422InsufficientBalance_when_availableBalanceCoversAmountButNotFee() {
        // given: du 500.000 nhung thieu phi 2.200
        ACCOUNT_SERVICE.givenAccount(sourceAccount, cif, "ACTIVE", "502199.99");

        // when
        var response = create(interbankBody("500000"), ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().code()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM TRANSFER WHERE CIF = ?", Integer.class, cif))
                .isZero();
    }

    @Test
    void should_return201_when_availableBalanceEqualsAmountPlusFee() {
        // given
        ACCOUNT_SERVICE.givenAccount(sourceAccount, cif, "ACTIVE", "502200.00");

        // when
        var response = create(interbankBody("500000"), TransferResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private LocalDate tomorrow() {
        return LocalDate.now(clock.withZone(BankZone.ZONE)).plusDays(1);
    }

    private BigDecimal storedFeeOf(String transferId) {
        return jdbcTemplate.queryForObject(
                "SELECT FEE FROM TRANSFER WHERE TRANSFER_ID = ?", BigDecimal.class, transferId);
    }

    private <T> ResponseEntity<T> create(String body, Class<T> responseType) {
        return client.post()
                .uri(TRANSFERS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Roles", "CUSTOMER")
                .header("X-Cif", cif)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(body)
                .retrieve()
                .toEntity(responseType);
    }

    private String interbankBody(String amount) {
        return """
                {"sourceAccount":"%s","destAccount":"0123456789","destBankCode":"%s","amount":%s}""".formatted(sourceAccount, NAPAS_BANK_CODE, amount);
    }

    private String internalBody(String amount) {
        return """
                {"sourceAccount":"%s","destAccount":"%s","amount":%s}""".formatted(sourceAccount, destAccount, amount);
    }

    private static String randomAccountNo() {
        return "1000%08d".formatted(ThreadLocalRandom.current().nextLong(1, 100_000_000L));
    }

    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }
}
