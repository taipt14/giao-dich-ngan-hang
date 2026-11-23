package vn.bank.account.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import vn.bank.account.application.BalanceCacheEvictor;
import vn.bank.account.infrastructure.cache.RedisKeys;
import vn.bank.common.error.ErrorResponse;
import vn.bank.common.test.AbstractIntegrationTest;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AccountQueryControllerIT extends AbstractIntegrationTest {

    private static final String ACCOUNTS_PATH = "/api/v1/accounts";
    private static final String BALANCE_PATH = ACCOUNTS_PATH + "/{accountNo}/balance";
    private static final String ROLES_HEADER = "X-Roles";
    private static final String CIF_HEADER = "X-Cif";
    private static final Duration BALANCE_TTL = Duration.ofMinutes(5);

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final TransactionTemplate transactionTemplate;
    private final BalanceCacheEvictor balanceCacheEvictor;

    @LocalServerPort
    private int port;

    private RestClient client;

    AccountQueryControllerIT(
            @Autowired JdbcTemplate jdbcTemplate,
            @Autowired StringRedisTemplate redisTemplate,
            @Autowired TransactionTemplate transactionTemplate,
            @Autowired BalanceCacheEvictor balanceCacheEvictor) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.transactionTemplate = transactionTemplate;
        this.balanceCacheEvictor = balanceCacheEvictor;
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
    void should_listOnlyOwnAccounts_when_customerListsAccounts() {
        // given
        var cif = randomCif();
        var first = givenAccount(cif, "1000000.00");
        var second = givenAccount(cif, "0.00");
        givenAccount(randomCif(), "5.00");

        // when
        var response = client.get()
                .uri(ACCOUNTS_PATH)
                .header(ROLES_HEADER, "CUSTOMER")
                .header(CIF_HEADER, cif)
                .retrieve()
                .toEntity(AccountListResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items())
                .extracting(AccountResponse::accountNo)
                .containsExactlyInAnyOrder(first, second);
        assertThat(response.getBody().items())
                .filteredOn(account -> account.accountNo().equals(first))
                .singleElement()
                .satisfies(account -> assertThat(account.balance()).isEqualByComparingTo("1000000.00"));
    }

    @Test
    void should_returnEmptyList_when_customerHasNoAccount() {
        // when
        var response = client.get()
                .uri(ACCOUNTS_PATH)
                .header(ROLES_HEADER, "CUSTOMER")
                .header(CIF_HEADER, randomCif())
                .retrieve()
                .toEntity(AccountListResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).isEmpty();
    }

    @Test
    void should_returnBalanceAndAvailableBalance_when_customerReadsOwnAccount() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif, "2500000.00");

        // when
        var response = getBalance(cif, accountNo, AccountBalanceResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body.accountNo()).isEqualTo(accountNo);
        assertThat(body.currency()).isEqualTo("VND");
        assertThat(body.balance()).isEqualByComparingTo("2500000.00");
        assertThat(body.availableBalance()).isEqualByComparingTo("2500000.00");
    }

    @Test
    void should_return403AccountAccessDenied_when_customerReadsOtherCustomersAccount() {
        // given
        var accountNo = givenAccount(randomCif(), "100.00");

        // when
        var response = getBalance(randomCif(), accountNo, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "ACCOUNT_ACCESS_DENIED");
    }

    @Test
    void should_return403AccountAccessDenied_when_customerReadsGlAccount() {
        // given: tai khoan GL CASH_VAULT tao san boi migration, khong co CIF

        // when
        var response = getBalance(randomCif(), "CASH_VAULT", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "ACCOUNT_ACCESS_DENIED");
    }

    @Test
    void should_return403AccountAccessDenied_when_otherCustomersAccountAlreadyCached() {
        // given
        var owner = randomCif();
        var accountNo = givenAccount(owner, "100.00");
        assertThat(getBalance(owner, accountNo, AccountBalanceResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // when
        var response = getBalance(randomCif(), accountNo, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "ACCOUNT_ACCESS_DENIED");
    }

    @Test
    void should_return404AccountNotFound_when_accountDoesNotExist() {
        // when
        var response = getBalance(randomCif(), randomAccountNo(), ErrorResponse.class);

        // then
        assertError(response, HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
    }

    @Test
    void should_return403AccessDenied_when_adminWithoutCifReadsBalance() {
        // given
        var accountNo = givenAccount(randomCif(), "100.00");

        // when
        var response = client.get()
                .uri(BALANCE_PATH, accountNo)
                .header(ROLES_HEADER, "ADMIN")
                .retrieve()
                .toEntity(ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void should_cacheBalanceWithFiveMinuteTtl_when_balanceRead() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif, "100.00");

        // when
        getBalance(cif, accountNo, AccountBalanceResponse.class);

        // then
        var ttl = redisTemplate.getExpire(RedisKeys.balance(accountNo));
        assertThat(ttl).isPositive().isLessThanOrEqualTo(BALANCE_TTL.toSeconds());
    }

    @Test
    void should_notQueryDb_when_balanceReadSecondTime() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif, "100.00");
        getBalance(cif, accountNo, AccountBalanceResponse.class);
        // Doi DB ma khong xoa cache: lan doc thu 2 van tra gia tri cu chung to khong truy van DB
        jdbcTemplate.update(
                "UPDATE ACCOUNT SET BALANCE = 999, AVAILABLE_BALANCE = 999 WHERE ACCOUNT_NO = ?", accountNo);

        // when
        var response = getBalance(cif, accountNo, AccountBalanceResponse.class);

        // then
        assertThat(response.getBody().balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void should_returnNewBalance_when_balanceChangedAndCommitted() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif, "100.00");
        getBalance(cif, accountNo, AccountBalanceResponse.class);

        // when
        transactionTemplate.executeWithoutResult(status -> changeBalance(accountNo, "750.00"));
        var response = getBalance(cif, accountNo, AccountBalanceResponse.class);

        // then
        assertThat(response.getBody().balance()).isEqualByComparingTo("750.00");
        assertThat(response.getBody().availableBalance()).isEqualByComparingTo("750.00");
    }

    @Test
    void should_keepCachedBalance_when_balanceChangeRolledBack() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif, "100.00");
        getBalance(cif, accountNo, AccountBalanceResponse.class);

        // when
        var thrown = catchThrowable(() -> transactionTemplate.executeWithoutResult(status -> {
            changeBalance(accountNo, "750.00");
            throw new IllegalStateException("Posting failed");
        }));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(redisTemplate.hasKey(RedisKeys.balance(accountNo))).isTrue();
        assertThat(getBalance(cif, accountNo, AccountBalanceResponse.class)
                        .getBody()
                        .balance())
                .isEqualByComparingTo("100.00");
    }

    // Gia lap use case doi so du (posting BANK-031): cap nhat DB va dang ky xoa cache trong cung transaction
    private void changeBalance(String accountNo, String amount) {
        var value = new BigDecimal(amount);
        jdbcTemplate.update(
                "UPDATE ACCOUNT SET BALANCE = ?, AVAILABLE_BALANCE = ? WHERE ACCOUNT_NO = ?", value, value, accountNo);
        balanceCacheEvictor.evictAfterCommit(accountNo);
    }

    private <T> ResponseEntity<T> getBalance(String cif, String accountNo, Class<T> responseType) {
        return client.get()
                .uri(BALANCE_PATH, accountNo)
                .header(ROLES_HEADER, "CUSTOMER")
                .header(CIF_HEADER, cif)
                .retrieve()
                .toEntity(responseType);
    }

    private String givenAccount(String cif, String balance) {
        var accountNo = randomAccountNo();
        var value = new BigDecimal(balance);
        jdbcTemplate.update("""
                INSERT INTO ACCOUNT (ACCOUNT_NO, CIF, ACCOUNT_TYPE, CURRENCY, BALANCE, AVAILABLE_BALANCE, STATUS,
                                     VERSION, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, 'CURRENT', 'VND', ?, ?, 'ACTIVE', 0, SYSTIMESTAMP, SYSTIMESTAMP)""", accountNo, cif, value, value);
        return accountNo;
    }

    private static void assertError(ResponseEntity<ErrorResponse> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(code);
    }

    // Container Oracle/Redis co the duoc dung lai giua cac lan chay: moi test tu sinh du lieu rieng.
    // Tien to 2000 de khong trung so tai khoan sinh tu sequence (1000...)
    private static String randomAccountNo() {
        return "2000%08d".formatted(ThreadLocalRandom.current().nextLong(1, 100_000_000L));
    }

    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }
}
