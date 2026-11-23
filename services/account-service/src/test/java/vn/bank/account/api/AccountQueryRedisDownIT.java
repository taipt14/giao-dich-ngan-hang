package vn.bank.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import vn.bank.common.test.AbstractIntegrationTest;

/**
 * Redis chet: tro service toi cong khong co Redis nao lang nghe (url ghi de host/port cua container dung chung), khong
 * dung container that vi cac test class khac trong JVM con dung no.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.data.redis.url=redis://localhost:1")
class AccountQueryRedisDownIT extends AbstractIntegrationTest {

    private static final String ROLES_HEADER = "X-Roles";
    private static final String CIF_HEADER = "X-Cif";

    private final JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    private RestClient client;

    AccountQueryRedisDownIT(@Autowired JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void createClient() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void should_return200WithBalanceFromDb_when_redisDown() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif);

        // when
        var first = getBalance(cif, accountNo);
        var second = getBalance(cif, accountNo);

        // then
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().balance()).isEqualByComparingTo("300000.00");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_return200_when_redisDownAndCustomerListsAccounts() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif);

        // when
        var response = client.get()
                .uri("/api/v1/accounts")
                .header(ROLES_HEADER, "CUSTOMER")
                .header(CIF_HEADER, cif)
                .retrieve()
                .toEntity(AccountListResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items())
                .extracting(AccountResponse::accountNo)
                .containsExactly(accountNo);
    }

    private ResponseEntity<AccountBalanceResponse> getBalance(String cif, String accountNo) {
        return client.get()
                .uri("/api/v1/accounts/{accountNo}/balance", accountNo)
                .header(ROLES_HEADER, "CUSTOMER")
                .header(CIF_HEADER, cif)
                .retrieve()
                .toEntity(AccountBalanceResponse.class);
    }

    private String givenAccount(String cif) {
        var accountNo = "3000%08d".formatted(ThreadLocalRandom.current().nextLong(1, 100_000_000L));
        jdbcTemplate.update("""
                INSERT INTO ACCOUNT (ACCOUNT_NO, CIF, ACCOUNT_TYPE, CURRENCY, BALANCE, AVAILABLE_BALANCE, STATUS,
                                     VERSION, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, 'CURRENT', 'VND', 300000, 300000, 'ACTIVE', 0, SYSTIMESTAMP, SYSTIMESTAMP)""", accountNo, cif);
        return accountNo;
    }

    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }
}
