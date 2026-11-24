package vn.bank.transfer.infrastructure.client;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.bank.common.error.ErrorResponse;
import vn.bank.common.error.TechnicalException;
import vn.bank.transfer.config.AccountServiceProperties;
import vn.bank.transfer.domain.AccountNumberMasker;
import vn.bank.transfer.domain.AccountSnapshot;
import vn.bank.transfer.domain.PostingLine;
import vn.bank.transfer.domain.TransferErrorCode;

/**
 * Goi API noi bo cua account-service: tra cuu tai khoan (chu so huu, trang thai, so du kha dung) va ghi but toan cho
 * lenh chuyen khoan.
 */
@Slf4j
@Component
public class AccountClient {

    static final String ACCOUNT_PATH = "/internal/accounts/{accountNo}";
    static final String POSTINGS_PATH = "/internal/postings";
    static final String TRANSFER_REFERENCE_TYPE = "TRANSFER";

    // 4xx tam thoi: account-service da rollback, goi lai co the thanh cong (vi du xung dot khi sua dong thoi)
    private static final Set<HttpStatus> TRANSIENT_CLIENT_ERRORS =
            Set.of(HttpStatus.REQUEST_TIMEOUT, HttpStatus.CONFLICT, HttpStatus.TOO_MANY_REQUESTS);

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

    /**
     * Ghi journal {@code TRANSFER} cho lenh {@code transferId}, idempotent theo transferId o account-service. 2xx →
     * da ghi; 4xx (tru 408, 409, 429) → bi tu choi, account-service da rollback nen tien chua bi tru. Con lai (timeout,
     * 5xx, khong ket noi duoc, 4xx tam thoi) → {@code ACCOUNT_SERVICE_UNAVAILABLE}: chua biet da ghi hay chua.
     */
    public PostingOutcome submitPosting(String transferId, String description, List<PostingLine> lines) {
        var request = new CreatePostingRequest(TRANSFER_REFERENCE_TYPE, transferId, description, toRequests(lines));
        try {
            restClient
                    .post()
                    .uri(POSTINGS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            return PostingOutcome.success();
        } catch (HttpClientErrorException e) {
            if (TRANSIENT_CLIENT_ERRORS.contains(
                    HttpStatus.resolve(e.getStatusCode().value()))) {
                throw postingUnavailable(transferId, e);
            }
            return PostingOutcome.rejected(errorCodeOf(transferId, e));
        } catch (RestClientException e) {
            throw postingUnavailable(transferId, e);
        }
    }

    private static List<PostingLineRequest> toRequests(List<PostingLine> lines) {
        return lines.stream()
                .map(line -> new PostingLineRequest(line.accountNo(), line.direction(), line.amount()))
                .toList();
    }

    // Body khong phai ErrorResponse (vi du loi tu proxy) thi khong co ma loi, noi goi xu ly nhu ma khong mong doi
    private static String errorCodeOf(String transferId, HttpClientErrorException e) {
        try {
            var error = e.getResponseBodyAs(ErrorResponse.class);
            return error == null ? null : error.code();
        } catch (RestClientException conversionError) {
            log.warn("Unreadable posting rejection for transfer {}, status {}", transferId, e.getStatusCode());
            return null;
        }
    }

    private static TechnicalException postingUnavailable(String transferId, RestClientException e) {
        return new TechnicalException(
                TransferErrorCode.ACCOUNT_SERVICE_UNAVAILABLE, "Cannot post transfer " + transferId, e);
    }
}
