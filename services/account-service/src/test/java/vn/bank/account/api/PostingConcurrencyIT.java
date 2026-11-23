package vn.bank.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import vn.bank.common.error.ErrorResponse;
import vn.bank.common.test.AbstractIntegrationTest;

/**
 * Nhieu posting dong thoi tren cung tai khoan (BANK-032), qua {@code POST /internal/postings} voi Oracle that. Moi test
 * dung tai khoan va referenceId rieng (LEDGER_ENTRY khong xoa duoc); tai khoan duoc nap tien bang posting DEPOSIT tu
 * CASH_VAULT de tong so du moi tai khoan van bang 0.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PostingConcurrencyIT extends AbstractIntegrationTest {

    private static final String POSTINGS_PATH = "/internal/postings";
    private static final String CASH_VAULT = "CASH_VAULT";
    // Gap doi maximum-pool-size (10): request vua cho connection vua cho khoa, nhung moi posting chi giu khoa rat ngan
    // nen khong cham connection-timeout/lock timeout 5s
    private static final int THREADS = 20;
    private static final long TASK_TIMEOUT_SECONDS = 60;

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @LocalServerPort
    private int port;

    private RestClient client;

    PostingConcurrencyIT(@Autowired JdbcTemplate jdbcTemplate, @Autowired DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @BeforeEach
    void createClient() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void should_runOnRealOracle_when_concurrencyTestsExecute() throws SQLException {
        // given / when
        try (var connection = dataSource.getConnection()) {
            var productName = connection.getMetaData().getDatabaseProductName();

            // then
            assertThat(productName).isEqualTo("Oracle");
        }
    }

    @Test
    void should_succeedTenAndRejectTenWithInsufficientBalance_when_twentyThreadsDebit100kFrom1M() throws Exception {
        // given
        var source = givenFundedAccount("1000000.00");
        var dest = givenAccount();
        var tasks = new ArrayList<Callable<Outcome>>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> transfer(source, dest, "100000.00"));
        }

        // when
        var outcomes = runConcurrently(tasks);

        // then
        assertThat(outcomes).filteredOn(Outcome::isCreated).hasSize(10);
        assertThat(outcomes)
                .filteredOn(outcome -> !outcome.isCreated())
                .hasSize(10)
                .allSatisfy(outcome -> {
                    assertThat(outcome.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(outcome.code()).isEqualTo("INSUFFICIENT_BALANCE");
                });
        assertThat(balanceOf(source)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(dest)).isEqualByComparingTo("1000000.00");
        assertThat(lastBalanceAfter(source)).isEqualByComparingTo("0.00");
        assertThat(countEntries(source)).isEqualTo(11);
    }

    @Test
    void should_notDeadlockAndKeepTotal_when_aToBAndBToATransfer50TimesConcurrently() throws Exception {
        // given
        var accountA = givenFundedAccount("1000000.00");
        var accountB = givenFundedAccount("1000000.00");
        var tasks = new ArrayList<Callable<Outcome>>();
        for (int i = 0; i < 50; i++) {
            tasks.add(() -> transfer(accountA, accountB, "10000.00"));
            tasks.add(() -> transfer(accountB, accountA, "10000.00"));
        }

        // when
        var outcomes = runConcurrently(tasks);

        // then: deadlock (ORA-00060) hay het thoi gian cho khoa deu lam mot request khong phai 201
        assertThat(outcomes)
                .hasSize(100)
                .allSatisfy(outcome -> assertThat(outcome.status()).isEqualTo(HttpStatus.CREATED));
        assertThat(balanceOf(accountA).add(balanceOf(accountB))).isEqualByComparingTo("2000000.00");
        assertThat(balanceOf(accountA)).isEqualByComparingTo("1000000.00");
        assertThat(lastBalanceAfter(accountA)).isEqualByComparingTo(balanceOf(accountA));
        assertThat(lastBalanceAfter(accountB)).isEqualByComparingTo(balanceOf(accountB));
    }

    @Test
    void should_return503AccountBusyAfterLockTimeout_when_accountLockedByAnotherTransaction() throws SQLException {
        // given: mot transaction khac giu khoa tai khoan nguon
        var source = givenFundedAccount("1000000.00");
        var dest = givenAccount();
        try (var holder = dataSource.getConnection()) {
            holder.setAutoCommit(false);
            try (var lock = holder.prepareStatement("SELECT ACCOUNT_NO FROM ACCOUNT WHERE ACCOUNT_NO = ? FOR UPDATE")) {
                lock.setString(1, source);
                lock.executeQuery().close();
            }

            // when
            var startedAt = System.nanoTime();
            var outcome = transfer(source, dest, "100000.00");
            var waited = Duration.ofNanos(System.nanoTime() - startedAt);
            holder.rollback();

            // then: cho dung ~5s (FOR UPDATE WAIT 5), truoc timeout transaction 10s
            assertThat(outcome.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(outcome.code()).isEqualTo("ACCOUNT_BUSY");
            assertThat(waited).isBetween(Duration.ofMillis(4500), Duration.ofSeconds(9));
        }
        assertThat(balanceOf(source)).isEqualByComparingTo("1000000.00");
        assertThat(countEntries(dest)).isZero();
    }

    // Moi task cho den khi tat ca thread san sang roi moi gui, de cac posting thuc su chen nhau
    private static List<Outcome> runConcurrently(List<Callable<Outcome>> tasks) throws Exception {
        var ready = new CountDownLatch(THREADS);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<Outcome>>();
        try (var executor = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < tasks.size(); i++) {
                var task = tasks.get(i);
                var gated = i < THREADS;
                futures.add(executor.submit(() -> {
                    if (gated) {
                        ready.countDown();
                        start.await();
                    }
                    return task.call();
                }));
            }
            assertThat(ready.await(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var outcomes = new ArrayList<Outcome>();
            for (var future : futures) {
                outcomes.add(future.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return outcomes;
        }
    }

    private Outcome transfer(String from, String to, String amount) {
        var body = """
                {"referenceType":"TRANSFER","referenceId":"%s","description":"Chuyen tien dong thoi IT",
                 "lines":[%s,%s]}""".formatted(newReferenceId(), line(from, "DEBIT", amount), line(to, "CREDIT", amount));
        return post(body);
    }

    private Outcome post(String body) {
        return client.post()
                .uri(POSTINGS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> {
                    var status = response.getStatusCode();
                    var code = status.isError()
                            ? response.bodyTo(ErrorResponse.class).code()
                            : null;
                    return new Outcome(status, code);
                });
    }

    private String givenFundedAccount(String amount) {
        var accountNo = givenAccount();
        var body =
                """
                {"referenceType":"DEPOSIT","referenceId":"%s","description":"Nap tien IT",
                 "lines":[%s,%s]}""".formatted(newReferenceId(), line(CASH_VAULT, "DEBIT", amount), line(accountNo, "CREDIT", amount));
        assertThat(post(body).isCreated()).isTrue();
        return accountNo;
    }

    private String givenAccount() {
        var accountNo = randomAccountNo();
        jdbcTemplate.update("""
                INSERT INTO ACCOUNT (ACCOUNT_NO, CIF, ACCOUNT_TYPE, CURRENCY, BALANCE, AVAILABLE_BALANCE, STATUS,
                                     VERSION, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, 'CURRENT', 'VND', 0, 0, 'ACTIVE', 0, SYSTIMESTAMP, SYSTIMESTAMP)""", accountNo, randomCif());
        return accountNo;
    }

    private static String line(String accountNo, String direction, String amount) {
        return """
                {"accountNo":"%s","direction":"%s","amount":%s}""".formatted(accountNo, direction, amount);
    }

    private BigDecimal balanceOf(String accountNo) {
        return jdbcTemplate.queryForObject(
                "SELECT BALANCE FROM ACCOUNT WHERE ACCOUNT_NO = ?", BigDecimal.class, accountNo);
    }

    private BigDecimal lastBalanceAfter(String accountNo) {
        return jdbcTemplate.queryForObject(
                "SELECT BALANCE_AFTER FROM LEDGER_ENTRY WHERE ACCOUNT_NO = ? ORDER BY ENTRY_ID DESC FETCH FIRST 1 ROW ONLY",
                BigDecimal.class,
                accountNo);
    }

    private Integer countEntries(String accountNo) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM LEDGER_ENTRY WHERE ACCOUNT_NO = ?", Integer.class, accountNo);
    }

    private static String newReferenceId() {
        return UUID.randomUUID().toString();
    }

    // Tien to 6000 de khong trung so tai khoan sinh tu sequence (1000...) va cua test khac (2000... den 5000...)
    private static String randomAccountNo() {
        return "6000%08d".formatted(ThreadLocalRandom.current().nextLong(1, 100_000_000L));
    }

    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }

    private record Outcome(HttpStatusCode status, String code) {

        boolean isCreated() {
            return HttpStatus.CREATED.isSameCodeAs(status);
        }
    }
}
