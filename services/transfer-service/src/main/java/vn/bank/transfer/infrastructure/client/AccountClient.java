package vn.bank.transfer.infrastructure.client;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.bank.common.error.TechnicalException;
import vn.bank.transfer.config.AccountServiceProperties;
import vn.bank.transfer.domain.AccountNumberMasker;
import vn.bank.transfer.domain.AccountSnapshot;
import vn.bank.transfer.domain.TransferErrorCode;

/** Goi API noi bo cua account-service de biet tai khoan thuoc ai, dang o trang thai nao va so du kha dung. */
@Component
public class AccountClient {

    static final String ACCOUNT_PATH = "/internal/accounts/{accountNo}";

    private final RestClient restClient;

    public AccountClient(RestClient.Builder restClientBuilder, AccountServiceProperties properties) {
        this.restClient =
                restClientBuilder.baseUrl(properties.baseUrl().toString()).build();
    }

    /**
     * Tai khoan khong ton tai (404) thi tra rong. account-service loi, timeout hoac khong ket noi duoc thi nem
     * {@code ACCOUNT_SERVICE_UNAVAILABLE} (503, client duoc retry).
     */
    public Optional<AccountSnapshot> findAccount(String accountNo) {
        var masked = AccountNumberMasker.mask(accountNo);
        try {
            var response =
                    restClient.get().uri(ACCOUNT_PATH, accountNo).retrieve().body(AccountInfoResponse.class);
            if (response == null) {
                throw new TechnicalException(
                        TransferErrorCode.ACCOUNT_SERVICE_UNAVAILABLE,
                        "Empty response from account-service for " + masked,
                        null);
            }
            return Optional.of(new AccountSnapshot(
                    response.accountNo(), response.cif(), response.status(), response.availableBalance()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            throw new TechnicalException(
                    TransferErrorCode.ACCOUNT_SERVICE_UNAVAILABLE, "Cannot get account " + masked, e);
        }
    }
}
