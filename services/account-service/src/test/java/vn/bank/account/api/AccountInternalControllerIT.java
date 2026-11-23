package vn.bank.account.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import vn.bank.account.domain.AccountStatus;
import vn.bank.account.domain.AccountType;
import vn.bank.common.error.ErrorResponse;
import vn.bank.common.test.AbstractIntegrationTest;

/** {@code GET /internal/accounts/{accountNo}}: transfer-service tra chu so huu va trang thai tai khoan (BANK-041). */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AccountInternalControllerIT extends AbstractIntegrationTest {

    private static final String ACCOUNT_PATH = "/internal/accounts/{accountNo}";

    private final JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    private RestClient client;

    AccountInternalControllerIT(@Autowired JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
    void should_returnOwnerAndStatus_when_accountExists() {
        // given
        var accountNo = randomAccountNo();
        var cif = randomCif();
        givenAccount(accountNo, cif, "LOCKED");

        // when: khong co header X-Roles van doc duoc vi API noi bo khong qua gateway
        var response = client.get().uri(ACCOUNT_PATH, accountNo).retrieve().toEntity(AccountResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var account = response.getBody();
        assertThat(account).isNotNull();
        assertThat(account.accountNo()).isEqualTo(accountNo);
        assertThat(account.cif()).isEqualTo(cif);
        assertThat(account.accountType()).isEqualTo(AccountType.CURRENT);
        assertThat(account.status()).isEqualTo(AccountStatus.LOCKED);
        assertThat(account.availableBalance()).isEqualByComparingTo(new BigDecimal("150000"));
    }

    @Test
    void should_return404AccountNotFound_when_accountDoesNotExist() {
        // given
        var accountNo = randomAccountNo();

        // when
        var response = client.get().uri(ACCOUNT_PATH, accountNo).retrieve().toEntity(ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("ACCOUNT_NOT_FOUND");
    }

    private void givenAccount(String accountNo, String cif, String status) {
        jdbcTemplate.update("""
                INSERT INTO ACCOUNT (ACCOUNT_NO, CIF, ACCOUNT_TYPE, CURRENCY, BALANCE, AVAILABLE_BALANCE, STATUS,
                                     VERSION, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, 'CURRENT', 'VND', 150000, 150000, ?, 0, SYSTIMESTAMP, SYSTIMESTAMP)""", accountNo, cif, status);
    }

    // Container Oracle co the duoc dung lai giua cac lan chay: moi test tu sinh du lieu rieng.
    // Tien to 1100 de khong trung so tai khoan sinh tu sequence (1000...) va cua cac IT khac (2000... den 9000...)
    private static String randomAccountNo() {
        return "1100%08d".formatted(ThreadLocalRandom.current().nextLong(1, 100_000_000L));
    }

    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }
}
