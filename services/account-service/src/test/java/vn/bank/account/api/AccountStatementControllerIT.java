package vn.bank.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import vn.bank.account.domain.EntryDirection;
import vn.bank.account.domain.ReferenceType;
import vn.bank.account.support.MutableClock;
import vn.bank.common.error.ErrorResponse;
import vn.bank.common.test.AbstractIntegrationTest;

/**
 * {@code GET /api/v1/accounts/{accountNo}/statements} voi Oracle that. CREATED_AT cua but toan lay tu {@link MutableClock}
 * nen test ghi duoc dong so cai o dung thoi diem can kiem tra. LEDGER_ENTRY khong xoa duoc nen moi test dung tai khoan
 * rieng, nap tien bang posting DEPOSIT tu CASH_VAULT.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AccountStatementControllerIT extends AbstractIntegrationTest {

    private static final String STATEMENTS_PATH = "/api/v1/accounts/{accountNo}/statements";
    private static final String POSTINGS_PATH = "/internal/postings";
    private static final String ROLES_HEADER = "X-Roles";
    private static final String CIF_HEADER = "X-Cif";
    private static final String CASH_VAULT = "CASH_VAULT";
    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");
    private static final MutableClock CLOCK = new MutableClock(NOW);

    private final JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    private RestClient client;

    AccountStatementControllerIT(@Autowired JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @TestConfiguration
    static class MutableClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return CLOCK;
        }
    }

    @BeforeEach
    void setUp() {
        CLOCK.setInstant(NOW);
        // Khong nem exception voi 4xx/5xx de test doc duoc body loi
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
                .build();
    }

    @Test
    void should_returnLinesNewestFirstWithSignedAmountAndBalanceAfter_when_customerReadsOwnStatement() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif);
        at("2026-11-21T03:00:00Z", () -> deposit(accountNo, "1000000.00"));
        at("2026-11-21T04:00:00Z", () -> transferOut(accountNo, "300000.00"));

        // when
        var response = getStatement(cif, accountNo, "?from=2026-11-21&to=2026-11-21", StatementResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body.size()).isEqualTo(20);
        assertThat(body.nextCursor()).isNull();
        assertThat(body.items()).hasSize(2);
        var transfer = body.items().get(0);
        assertThat(transfer.createdAt()).isEqualTo(Instant.parse("2026-11-21T04:00:00Z"));
        assertThat(transfer.referenceType()).isEqualTo(ReferenceType.TRANSFER);
        assertThat(transfer.description()).isEqualTo("Chuyen tien IT");
        assertThat(transfer.direction()).isEqualTo(EntryDirection.DEBIT);
        assertThat(transfer.amount()).isEqualByComparingTo("-300000.00");
        assertThat(transfer.balanceAfter()).isEqualByComparingTo("700000.00");
        var deposit = body.items().get(1);
        assertThat(deposit.referenceType()).isEqualTo(ReferenceType.DEPOSIT);
        assertThat(deposit.description()).isEqualTo("Nap tien IT");
        assertThat(deposit.direction()).isEqualTo(EntryDirection.CREDIT);
        assertThat(deposit.amount()).isEqualByComparingTo("1000000.00");
        assertThat(deposit.balanceAfter()).isEqualByComparingTo("1000000.00");
        assertThat(deposit.entryId()).isLessThan(transfer.entryId());
    }

    @Test
    void should_pageByEntryIdCursorUntilNextCursorNull_when_moreEntriesThanSize() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif);
        for (var i = 1; i <= 5; i++) {
            deposit(accountNo, i + "00.00");
        }

        // when
        var first =
                getStatement(cif, accountNo, "?size=2", StatementResponse.class).getBody();
        var second = getStatement(cif, accountNo, "?size=2&cursor=" + first.nextCursor(), StatementResponse.class)
                .getBody();
        var third = getStatement(cif, accountNo, "?size=2&cursor=" + second.nextCursor(), StatementResponse.class)
                .getBody();

        // then
        var amounts = new ArrayList<String>();
        for (var page : new StatementResponse[] {first, second, third}) {
            page.items().forEach(line -> amounts.add(line.amount().toPlainString()));
        }
        assertThat(amounts).containsExactly("500.00", "400.00", "300.00", "200.00", "100.00");
        assertThat(first.items()).hasSize(2);
        assertThat(first.nextCursor()).isEqualTo(first.items().getLast().entryId());
        assertThat(second.items()).hasSize(2);
        assertThat(third.items()).hasSize(1);
        assertThat(third.nextCursor()).isNull();
    }

    @Test
    void should_includeOnlyEntriesOfVietnamBusinessDays_when_fromAndToGiven() {
        // given: ngay 2026-11-21 gio VN = [2026-11-20T17:00Z, 2026-11-21T17:00Z)
        var cif = randomCif();
        var accountNo = givenAccount(cif);
        at("2026-11-20T16:59:59Z", () -> deposit(accountNo, "1.00"));
        at("2026-11-20T17:00:00Z", () -> deposit(accountNo, "2.00"));
        at("2026-11-21T16:59:59Z", () -> deposit(accountNo, "3.00"));
        at("2026-11-21T17:00:00Z", () -> deposit(accountNo, "4.00"));

        // when
        var response = getStatement(cif, accountNo, "?from=2026-11-21&to=2026-11-21", StatementResponse.class);

        // then
        assertThat(response.getBody().items())
                .extracting(line -> line.amount().toPlainString())
                .containsExactly("3.00", "2.00");
    }

    @Test
    void should_returnLast30DaysIncludingToday_when_noDatesGiven() {
        // given: hom nay 2026-11-23 gio VN → mac dinh tu 2026-10-25 00:00 VN (2026-10-24T17:00Z)
        var cif = randomCif();
        var accountNo = givenAccount(cif);
        at("2026-10-24T16:59:59Z", () -> deposit(accountNo, "1.00"));
        at("2026-10-24T17:00:00Z", () -> deposit(accountNo, "2.00"));
        at("2026-11-23T02:00:00Z", () -> deposit(accountNo, "3.00"));

        // when
        var response = getStatement(cif, accountNo, "", StatementResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items())
                .extracting(line -> line.amount().toPlainString())
                .containsExactly("3.00", "2.00");
    }

    @Test
    void should_return400StatementRangeTooLarge_when_rangeExceeds90Days() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif);

        // when: 2026-08-23 → 2026-11-21 la 91 ngay tinh ca hai dau
        var response = getStatement(cif, accountNo, "?from=2026-08-23&to=2026-11-21", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "STATEMENT_RANGE_TOO_LARGE");
    }

    @Test
    void should_return200_when_rangeIsExactly90Days() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif);

        // when
        var response = getStatement(cif, accountNo, "?from=2026-08-24&to=2026-11-21", StatementResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).isEmpty();
    }

    @Test
    void should_return400StatementRangeInvalid_when_fromAfterTo() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif);

        // when
        var response = getStatement(cif, accountNo, "?from=2026-11-22&to=2026-11-21", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "STATEMENT_RANGE_INVALID");
    }

    @Test
    void should_return403AccountAccessDenied_when_customerReadsOtherCustomersStatement() {
        // given
        var accountNo = givenAccount(randomCif());
        deposit(accountNo, "100.00");

        // when
        var response = getStatement(randomCif(), accountNo, "", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "ACCOUNT_ACCESS_DENIED");
    }

    @Test
    void should_return403AccountAccessDenied_when_customerReadsGlStatement() {
        // when
        var response = getStatement(randomCif(), CASH_VAULT, "", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "ACCOUNT_ACCESS_DENIED");
    }

    @Test
    void should_return403AccessDenied_when_userHasNoCustomerRole() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif);

        // when
        var response = client.get()
                .uri(STATEMENTS_PATH, accountNo)
                .header(ROLES_HEADER, "ADMIN")
                .header(CIF_HEADER, cif)
                .retrieve()
                .toEntity(ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void should_return404AccountNotFound_when_accountDoesNotExist() {
        // when
        var response = getStatement(randomCif(), randomAccountNo(), "", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
    }

    @Test
    void should_return400ValidationFailed_when_sizeExceeds100() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif);

        // when
        var response = getStatement(cif, accountNo, "?size=101", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    }

    @Test
    void should_return400_when_dateNotIsoFormat() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif);

        // when
        var response = getStatement(cif, accountNo, "?from=21/11/2026", ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static void at(String instant, Runnable action) {
        CLOCK.setInstant(Instant.parse(instant));
        try {
            action.run();
        } finally {
            CLOCK.setInstant(NOW);
        }
    }

    private <T> ResponseEntity<T> getStatement(String cif, String accountNo, String query, Class<T> responseType) {
        return client.get()
                .uri(STATEMENTS_PATH + query, accountNo)
                .header(ROLES_HEADER, "CUSTOMER")
                .header(CIF_HEADER, cif)
                .retrieve()
                .toEntity(responseType);
    }

    private void deposit(String accountNo, String amount) {
        post("DEPOSIT", "Nap tien IT", line(CASH_VAULT, "DEBIT", amount), line(accountNo, "CREDIT", amount));
    }

    private void transferOut(String accountNo, String amount) {
        var dest = givenAccount(randomCif());
        post("TRANSFER", "Chuyen tien IT", line(accountNo, "DEBIT", amount), line(dest, "CREDIT", amount));
    }

    private void post(String referenceType, String description, String debit, String credit) {
        var body = """
                {"referenceType":"%s","referenceId":"%s","description":"%s","lines":[%s,%s]}""".formatted(referenceType, UUID.randomUUID(), description, debit, credit);
        var response = client.post()
                .uri(POSTINGS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(PostingResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private static String line(String accountNo, String direction, String amount) {
        return """
                {"accountNo":"%s","direction":"%s","amount":%s}""".formatted(accountNo, direction, amount);
    }

    private String givenAccount(String cif) {
        var accountNo = randomAccountNo();
        jdbcTemplate.update("""
                INSERT INTO ACCOUNT (ACCOUNT_NO, CIF, ACCOUNT_TYPE, CURRENCY, BALANCE, AVAILABLE_BALANCE, STATUS,
                                     VERSION, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, 'CURRENT', 'VND', 0, 0, 'ACTIVE', 0, SYSTIMESTAMP, SYSTIMESTAMP)""", accountNo, cif);
        return accountNo;
    }

    private static void assertError(ResponseEntity<ErrorResponse> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(code);
    }

    // Container Oracle co the duoc dung lai giua cac lan chay: moi test tu sinh du lieu rieng.
    // Tien to 7000 de khong trung so tai khoan sinh tu sequence (1000...) va cua test khac (2000... den 6000...)
    private static String randomAccountNo() {
        return "7000%08d".formatted(ThreadLocalRandom.current().nextLong(1, 100_000_000L));
    }

    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }
}
