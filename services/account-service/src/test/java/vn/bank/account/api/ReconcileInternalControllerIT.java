package vn.bank.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.web.client.RestClient;
import vn.bank.account.domain.ReconcileErrorType;
import vn.bank.account.support.MutableClock;
import vn.bank.common.error.ErrorResponse;
import vn.bank.common.test.AbstractIntegrationTest;

/**
 * {@code POST /internal/reconcile/run} va {@code PKG_RECONCILE} tren Oracle that. Schema dung chung voi test khac (so cai
 * khong xoa duoc, co tai khoan bi test khac sua BALANCE) nen moi test chi kiem loi cua tai khoan/journal do chinh no
 * tao. Tai khoan bi sua BALANCE duoc tra lai so du dung sau moi test.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = ReconcileInternalControllerIT.CUSTOM_CRON)
class ReconcileInternalControllerIT extends AbstractIntegrationTest {

    // Lich khac mac dinh 23:55 de kiem lich doc tu cau hinh; 22:30 gio VN khong trung luc test chay thuong
    static final String CUSTOM_CRON = "bank.account.reconcile.cron=0 30 22 * * *";

    private static final String RECONCILE_PATH = "/internal/reconcile/run";
    private static final String POSTINGS_PATH = "/internal/postings";
    private static final String ROLES_HEADER = "X-Roles";
    private static final String CASH_VAULT = "CASH_VAULT";
    // 10:00 ngay 2026-11-23 gio VN
    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2026-11-23");
    private static final MutableClock CLOCK = new MutableClock(NOW);

    private final JdbcTemplate jdbcTemplate;
    private final List<ScheduledTaskHolder> scheduledTaskHolders;
    private final Map<String, BigDecimal> tamperedBalances = new HashMap<>();

    @LocalServerPort
    private int port;

    private RestClient client;

    ReconcileInternalControllerIT(
            @Autowired JdbcTemplate jdbcTemplate, @Autowired List<ScheduledTaskHolder> scheduledTaskHolders) {
        this.jdbcTemplate = jdbcTemplate;
        this.scheduledTaskHolders = scheduledTaskHolders;
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

    // Tra lai so du dung de du lieu dung chung khong lech vinh vien vi test nay
    @AfterEach
    void restoreTamperedBalances() {
        tamperedBalances.forEach((accountNo, balance) ->
                jdbcTemplate.update("UPDATE ACCOUNT SET BALANCE = ? WHERE ACCOUNT_NO = ?", balance, accountNo));
        tamperedBalances.clear();
    }

    @Test
    void should_reportNoErrorForOwnAccountsAndJournals_when_dataIsConsistent() {
        // given
        var source = givenAccount();
        var dest = givenAccount();
        var depositJournal = deposit(source, "1000000.00");
        var transferJournal = post("TRANSFER", line(source, "DEBIT", "300000.00"), line(dest, "CREDIT", "300000.00"));

        // when
        var response = runReconcile(BUSINESS_DATE);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body.businessDate()).isEqualTo(BUSINESS_DATE);
        assertThat(body.errorCount()).isEqualTo(body.errors().size());
        assertThat(body.errors())
                .noneMatch(error -> Set.of(source, dest).contains(Objects.toString(error.accountNo(), "")))
                .noneMatch(error -> Objects.equals(depositJournal, error.journalId())
                        || Objects.equals(transferJournal, error.journalId()));
        assertThat(countResultRows(BUSINESS_DATE, source)).isZero();
        assertThat(countResultRows(BUSINESS_DATE, dest)).isZero();
        assertThat(countJournalResultRows(BUSINESS_DATE, depositJournal)).isZero();
        assertThat(countJournalResultRows(BUSINESS_DATE, transferJournal)).isZero();
    }

    @Test
    void should_reportExactlyTamperedAccount_when_balanceChangedBySql() {
        // given
        var tampered = givenAccount();
        var untouched = givenAccount();
        deposit(tampered, "1000000.00");
        deposit(untouched, "1000000.00");
        tamperBalance(tampered, "1000000.00", "1234000.00");

        // when
        var response = runReconcile(BUSINESS_DATE);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var ownErrors = errorsOf(response.getBody(), tampered, untouched);
        assertThat(ownErrors).hasSize(1);
        var error = ownErrors.getFirst();
        assertThat(error.errorType()).isEqualTo(ReconcileErrorType.BALANCE_MISMATCH);
        assertThat(error.accountNo()).isEqualTo(tampered);
        assertThat(error.journalId()).isNull();
        assertThat(error.expectedValue()).isEqualByComparingTo("1000000.00");
        assertThat(error.actualValue()).isEqualByComparingTo("1234000.00");
        assertThat(resultRow(BUSINESS_DATE, tampered))
                .containsEntry("ERROR_TYPE", "BALANCE_MISMATCH")
                .containsEntry("STATUS", "OPEN");
    }

    @Test
    void should_overwritePreviousResultWithoutDuplicate_when_runTwiceOnSameDay() {
        // given
        var accountNo = givenAccount();
        deposit(accountNo, "1000000.00");
        tamperBalance(accountNo, "1000000.00", "1234000.00");
        runReconcile(BUSINESS_DATE);
        var firstRow = resultRow(BUSINESS_DATE, accountNo);
        jdbcTemplate.update("UPDATE ACCOUNT SET BALANCE = 1500000 WHERE ACCOUNT_NO = ?", accountNo);

        // when
        var response = runReconcile(BUSINESS_DATE);

        // then
        assertThat(countResultRows(BUSINESS_DATE, accountNo)).isEqualTo(1);
        var secondRow = resultRow(BUSINESS_DATE, accountNo);
        assertThat(secondRow.get("RECONCILE_RESULT_ID")).isEqualTo(firstRow.get("RECONCILE_RESULT_ID"));
        assertThat((BigDecimal) secondRow.get("ACTUAL_VALUE")).isEqualByComparingTo("1500000.00");
        assertThat((BigDecimal) secondRow.get("EXPECTED_VALUE")).isEqualByComparingTo("1000000.00");
        assertThat(((BigDecimal) secondRow.get("RUN_ID")).longValue())
                .isGreaterThan(((BigDecimal) firstRow.get("RUN_ID")).longValue());
        assertThat(errorsOf(response.getBody(), accountNo))
                .singleElement()
                .satisfies(error -> assertThat(error.actualValue()).isEqualByComparingTo("1500000.00"));
    }

    @Test
    void should_markResolvedAndDropFromReport_when_mismatchFixedBeforeRerun() {
        // given
        var accountNo = givenAccount();
        deposit(accountNo, "1000000.00");
        tamperBalance(accountNo, "1000000.00", "1234000.00");
        runReconcile(BUSINESS_DATE);
        jdbcTemplate.update("UPDATE ACCOUNT SET BALANCE = 1000000 WHERE ACCOUNT_NO = ?", accountNo);

        // when
        var response = runReconcile(BUSINESS_DATE);

        // then
        assertThat(errorsOf(response.getBody(), accountNo)).isEmpty();
        assertThat(countResultRows(BUSINESS_DATE, accountNo)).isEqualTo(1);
        assertThat(resultRow(BUSINESS_DATE, accountNo)).containsEntry("STATUS", "RESOLVED");
    }

    @Test
    void should_reportUnbalancedJournal_when_debitTotalDiffersFromCreditTotal() {
        // given: chen thang bang SQL vi API posting tu choi journal khong can; BALANCE khop so cai de chi con loi
        // journal
        var accountNo = givenAccount();
        var journalId = givenCreditOnlyJournal(accountNo, "100.00", NOW);

        // when
        var response = runReconcile(BUSINESS_DATE);

        // then
        var ownErrors = response.getBody().errors().stream()
                .filter(error -> journalId.equals(error.journalId()) || accountNo.equals(error.accountNo()))
                .toList();
        assertThat(ownErrors).singleElement().satisfies(error -> {
            assertThat(error.errorType()).isEqualTo(ReconcileErrorType.JOURNAL_UNBALANCED);
            assertThat(error.accountNo()).isNull();
            assertThat(error.expectedValue()).isEqualByComparingTo("0.00");
            assertThat(error.actualValue()).isEqualByComparingTo("100.00");
        });
    }

    @Test
    void should_checkOnlyJournalsOfVietnamBusinessDay_when_journalCreatedJustAfterMidnightVietnam() {
        // given: 2026-11-23T17:00Z la 00:00 ngay 2026-11-24 gio VN
        var accountNo = givenAccount();
        var journalId = givenCreditOnlyJournal(accountNo, "100.00", Instant.parse("2026-11-23T17:00:00Z"));

        // when
        var sameUtcDay = runReconcile(BUSINESS_DATE);
        var nextVietnamDay = runReconcile(BUSINESS_DATE.plusDays(1));

        // then
        assertThat(sameUtcDay.getBody().errors()).noneMatch(error -> journalId.equals(error.journalId()));
        assertThat(nextVietnamDay.getBody().errors()).anyMatch(error -> journalId.equals(error.journalId()));
    }

    @Test
    void should_return403AccessDenied_when_userIsNotAdmin() {
        // when
        var response = client.post()
                .uri(RECONCILE_PATH + "?date=" + BUSINESS_DATE)
                .header(ROLES_HEADER, "CUSTOMER")
                .retrieve()
                .toEntity(ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void should_return400_when_dateMissing() {
        // when
        var response = client.post()
                .uri(RECONCILE_PATH)
                .header(ROLES_HEADER, "ADMIN")
                .retrieve()
                .toEntity(ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_return400_when_dateNotIsoFormat() {
        // when
        var response = client.post()
                .uri(RECONCILE_PATH + "?date=23/11/2026")
                .header(ROLES_HEADER, "ADMIN")
                .retrieve()
                .toEntity(ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_scheduleReconcileJobWithConfiguredCron_when_cronPropertySet() {
        // when
        var reconcileTasks = scheduledTaskHolders.stream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .map(scheduled -> scheduled.getTask())
                .filter(task -> task.getRunnable().toString().contains("ReconcileJob.run"))
                .toList();

        // then
        assertThat(reconcileTasks)
                .singleElement()
                .isInstanceOfSatisfying(
                        CronTask.class, task -> assertThat(task.getExpression()).isEqualTo("0 30 22 * * *"));
    }

    private ResponseEntity<ReconcileResponse> runReconcile(LocalDate date) {
        var response = client.post()
                .uri(RECONCILE_PATH + "?date=" + date)
                .header(ROLES_HEADER, "ADMIN")
                .retrieve()
                .toEntity(ReconcileResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response;
    }

    private static List<ReconcileErrorResponse> errorsOf(ReconcileResponse response, String... accountNos) {
        var own = List.of(accountNos);
        return response.errors().stream()
                .filter(error -> error.accountNo() != null && own.contains(error.accountNo()))
                .toList();
    }

    private void tamperBalance(String accountNo, String correctBalance, String tamperedBalance) {
        tamperedBalances.put(accountNo, new BigDecimal(correctBalance));
        jdbcTemplate.update(
                "UPDATE ACCOUNT SET BALANCE = ? WHERE ACCOUNT_NO = ?", new BigDecimal(tamperedBalance), accountNo);
    }

    private int countResultRows(LocalDate date, String accountNo) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM RECONCILE_RESULT WHERE BUSINESS_DATE = ? AND ACCOUNT_NO = ?",
                Integer.class,
                date,
                accountNo);
    }

    private int countJournalResultRows(LocalDate date, Long journalId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM RECONCILE_RESULT WHERE BUSINESS_DATE = ? AND JOURNAL_ID = ?",
                Integer.class,
                date,
                journalId);
    }

    private Map<String, Object> resultRow(LocalDate date, String accountNo) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM RECONCILE_RESULT WHERE BUSINESS_DATE = ? AND ACCOUNT_NO = ?", date, accountNo);
    }

    /** Journal chi co mot dong CREDIT: tong No 0, tong Co {@code amount}. BALANCE cap nhat khop so cai. */
    private Long givenCreditOnlyJournal(String accountNo, String amount, Instant createdAt) {
        var journalId = jdbcTemplate.queryForObject("SELECT SEQ_JOURNAL.NEXTVAL FROM DUAL", Long.class);
        var createdAtUtc = LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC);
        jdbcTemplate.update(
                "INSERT INTO JOURNAL (JOURNAL_ID, REFERENCE_TYPE, REFERENCE_ID, DESCRIPTION, CREATED_AT)"
                        + " VALUES (?, 'DEPOSIT', ?, 'Journal khong can IT', ?)",
                journalId,
                UUID.randomUUID().toString(),
                createdAtUtc);
        jdbcTemplate.update(
                "INSERT INTO LEDGER_ENTRY (ENTRY_ID, JOURNAL_ID, ACCOUNT_NO, DIRECTION, AMOUNT, BALANCE_AFTER, CREATED_AT)"
                        + " VALUES (SEQ_LEDGER_ENTRY.NEXTVAL, ?, ?, 'CREDIT', ?, ?, ?)",
                journalId,
                accountNo,
                new BigDecimal(amount),
                new BigDecimal(amount),
                createdAtUtc);
        jdbcTemplate.update(
                "UPDATE ACCOUNT SET BALANCE = ?, AVAILABLE_BALANCE = ? WHERE ACCOUNT_NO = ?",
                new BigDecimal(amount),
                new BigDecimal(amount),
                accountNo);
        return journalId;
    }

    private Long deposit(String accountNo, String amount) {
        return post("DEPOSIT", line(CASH_VAULT, "DEBIT", amount), line(accountNo, "CREDIT", amount));
    }

    private Long post(String referenceType, String debit, String credit) {
        var body = """
                {"referenceType":"%s","referenceId":"%s","description":"Doi soat IT","lines":[%s,%s]}""".formatted(referenceType, UUID.randomUUID(), debit, credit);
        var response = client.post()
                .uri(POSTINGS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(PostingResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().journalId();
    }

    private static String line(String accountNo, String direction, String amount) {
        return """
                {"accountNo":"%s","direction":"%s","amount":%s}""".formatted(accountNo, direction, amount);
    }

    private String givenAccount() {
        var accountNo = randomAccountNo();
        jdbcTemplate.update("""
                INSERT INTO ACCOUNT (ACCOUNT_NO, CIF, ACCOUNT_TYPE, CURRENCY, BALANCE, AVAILABLE_BALANCE, STATUS,
                                     VERSION, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, 'CURRENT', 'VND', 0, 0, 'ACTIVE', 0, SYSTIMESTAMP, SYSTIMESTAMP)""", accountNo, randomCif());
        return accountNo;
    }

    // Container Oracle co the duoc dung lai giua cac lan chay: moi test tu sinh du lieu rieng.
    // Tien to 8000 de khong trung so tai khoan sinh tu sequence (1000...) va cua test khac (2000... den 7000...)
    private static String randomAccountNo() {
        return "8000%08d".formatted(ThreadLocalRandom.current().nextLong(1, 100_000_000L));
    }

    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }
}
