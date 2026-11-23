package vn.bank.account.infrastructure.client;

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
import vn.bank.account.config.CustomerServiceProperties;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.account.domain.AccountHolder;
import vn.bank.common.error.BusinessException;
import vn.bank.common.error.TechnicalException;

class CustomerClientTest {

    private static final String BASE_URL = "http://customer-service";
    private static final String CIF = "C000000001";
    private static final String CUSTOMER_URL = BASE_URL + "/internal/customers/" + CIF;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final CustomerClient customerClient =
            new CustomerClient(builder, new CustomerServiceProperties(URI.create(BASE_URL)));

    @Test
    void should_returnHolderWithStatus_when_customerFound() {
        // given
        server.expect(requestTo(CUSTOMER_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"cif":"C000000001","status":"LOCKED"}""", MediaType.APPLICATION_JSON));

        // when
        var holder = customerClient.getAccountHolder(CIF);

        // then
        assertThat(holder).isEqualTo(new AccountHolder(CIF, "LOCKED"));
        server.verify();
    }

    @Test
    void should_throwCustomerNotFound_when_customerServiceReturns404() {
        // given
        server.expect(requestTo(CUSTOMER_URL)).andRespond(withResourceNotFound());

        // when
        var thrown = catchThrowable(() -> customerClient.getAccountHolder(CIF));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.CUSTOMER_NOT_FOUND);
    }

    @Test
    void should_throwCustomerServiceUnavailable_when_customerServiceReturns5xx() {
        // given
        server.expect(requestTo(CUSTOMER_URL)).andRespond(withServerError());

        // when
        var thrown = catchThrowable(() -> customerClient.getAccountHolder(CIF));

        // then
        assertThat(thrown)
                .isInstanceOf(TechnicalException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.CUSTOMER_SERVICE_UNAVAILABLE);
    }
}
