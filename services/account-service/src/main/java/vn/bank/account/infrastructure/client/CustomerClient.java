package vn.bank.account.infrastructure.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.bank.account.config.CustomerServiceProperties;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.account.domain.AccountHolder;
import vn.bank.common.error.BusinessException;
import vn.bank.common.error.TechnicalException;

/** Goi API noi bo cua customer-service de biet khach hang co ton tai va dang o trang thai nao. */
@Component
public class CustomerClient {

    static final String CUSTOMER_PATH = "/internal/customers/{cif}";

    private final RestClient restClient;

    public CustomerClient(RestClient.Builder restClientBuilder, CustomerServiceProperties properties) {
        this.restClient =
                restClientBuilder.baseUrl(properties.baseUrl().toString()).build();
    }

    /**
     * Khach hang khong ton tai thi nem {@code CUSTOMER_NOT_FOUND}. customer-service loi, timeout hoac khong ket noi
     * duoc thi nem {@code CUSTOMER_SERVICE_UNAVAILABLE} (503, client duoc retry).
     */
    public AccountHolder getAccountHolder(String cif) {
        try {
            var response = restClient.get().uri(CUSTOMER_PATH, cif).retrieve().body(CustomerStatusResponse.class);
            if (response == null) {
                throw new TechnicalException(
                        AccountErrorCode.CUSTOMER_SERVICE_UNAVAILABLE,
                        "Empty response from customer-service for " + cif,
                        null);
            }
            return new AccountHolder(response.cif(), response.status());
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(AccountErrorCode.CUSTOMER_NOT_FOUND, "Customer not found: " + cif, e);
        } catch (RestClientException e) {
            throw new TechnicalException(
                    AccountErrorCode.CUSTOMER_SERVICE_UNAVAILABLE, "Cannot get customer " + cif, e);
        }
    }
}
