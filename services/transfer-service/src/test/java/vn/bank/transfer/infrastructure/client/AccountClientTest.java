package vn.bank.transfer.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.bank.common.error.TechnicalException;
import vn.bank.transfer.config.AccountServiceProperties;
import vn.bank.transfer.domain.AccountSnapshot;
import vn.bank.transfer.domain.TransferErrorCode;

class AccountClientTest {

    private static final String BASE_URL = "http://account-service";
    private static final String ACCOUNT_NO = "100000000001";
    private static final String ACCOUNT_URL = BASE_URL + "/internal/accounts/" + ACCOUNT_NO;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final AccountClient accountClient =
            new AccountClient(builder, new AccountServiceProperties(URI.create(BASE_URL)));

    @Test
    void should_returnOwnerAndStatus_when_accountFound() {
        // given: response day du cua account-service, cac truong khong dung bi bo qua
        server.expect(requestTo(ACCOUNT_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"accountNo":"100000000001","cif":"C000000001","accountType":"CURRENT","currency":"VND",
                         "balance":150000.00,"availableBalance":150000.00,"status":"LOCKED",
                         "createdAt":"2026-11-23T03:00:00Z"}""", MediaType.APPLICATION_JSON));

        // when
        var account = accountClient.findAccount(ACCOUNT_NO);

        // then
        assertThat(account).contains(new AccountSnapshot(ACCOUNT_NO, "C000000001", "LOCKED"));
        server.verify();
    }

    @Test
    void should_returnEmpty_when_accountServiceReturns404() {
        // given
        server.expect(requestTo(ACCOUNT_URL)).andRespond(withResourceNotFound());

        // when
        var account = accountClient.findAccount(ACCOUNT_NO);

        // then
        assertThat(account).isEmpty();
    }

    @Test
    void should_throwAccountServiceUnavailable_when_accountServiceReturns5xx() {
        // given
        server.expect(requestTo(ACCOUNT_URL)).andRespond(withServerError());

        // when
        var thrown = catchThrowable(() -> accountClient.findAccount(ACCOUNT_NO));

        // then
        assertThat(thrown)
                .isInstanceOf(TechnicalException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.ACCOUNT_SERVICE_UNAVAILABLE);
    }
}
