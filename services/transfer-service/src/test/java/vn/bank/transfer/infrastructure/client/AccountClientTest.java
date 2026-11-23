package vn.bank.transfer.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import vn.bank.common.error.TechnicalException;
import vn.bank.transfer.config.AccountServiceProperties;
import vn.bank.transfer.domain.AccountSnapshot;
import vn.bank.transfer.domain.PostingDirection;
import vn.bank.transfer.domain.PostingLine;
import vn.bank.transfer.domain.TransferErrorCode;

class AccountClientTest {

    private static final String BASE_URL = "http://account-service";
    private static final String ACCOUNT_NO = "100000000001";
    private static final String ACCOUNT_URL = BASE_URL + "/internal/accounts/" + ACCOUNT_NO;
    private static final String POSTINGS_URL = BASE_URL + "/internal/postings";
    private static final String TRANSFER_ID = "7f1c2a9e-1d2b-4c3d-9e8f-0a1b2c3d4e5f";
    private static final List<PostingLine> LINES = List.of(
            new PostingLine(ACCOUNT_NO, PostingDirection.DEBIT, new BigDecimal("300000.00")),
            new PostingLine("100000000002", PostingDirection.CREDIT, new BigDecimal("300000.00")));

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final AccountClient accountClient =
            new AccountClient(builder, new AccountServiceProperties(URI.create(BASE_URL)));

    @Test
    void should_returnOwnerStatusAndAvailableBalance_when_accountFound() {
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
        assertThat(account)
                .contains(new AccountSnapshot(ACCOUNT_NO, "C000000001", "LOCKED", new BigDecimal("150000.00")));
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

    @Test
    void should_postTransferJournalAndReturnPosted_when_accountServiceReturns201() {
        // given
        server.expect(requestTo(POSTINGS_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"referenceType":"TRANSFER","referenceId":"%s","description":"Chuyen tien",
                         "lines":[{"accountNo":"100000000001","direction":"DEBIT","amount":300000.00},
                                  {"accountNo":"100000000002","direction":"CREDIT","amount":300000.00}]}""".formatted(TRANSFER_ID), JsonCompareMode.STRICT))
                .andRespond(withStatus(HttpStatus.CREATED));

        // when
        var outcome = accountClient.submitPosting(TRANSFER_ID, "Chuyen tien", LINES);

        // then
        assertThat(outcome).isEqualTo(PostingOutcome.success());
        server.verify();
    }

    @Test
    void should_returnPosted_when_accountServiceReturns200ForExistingJournal() {
        // given: posting lap lai cung transferId
        server.expect(requestTo(POSTINGS_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // when
        var outcome = accountClient.submitPosting(TRANSFER_ID, "Chuyen tien", LINES);

        // then
        assertThat(outcome.posted()).isTrue();
    }

    @Test
    void should_returnRejectedWithErrorCode_when_accountServiceReturns422() {
        // given
        server.expect(requestTo(POSTINGS_URL))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"INSUFFICIENT_BALANCE","message":"Insufficient balance","details":[]}"""));

        // when
        var outcome = accountClient.submitPosting(TRANSFER_ID, "Chuyen tien", LINES);

        // then
        assertThat(outcome).isEqualTo(PostingOutcome.rejected("INSUFFICIENT_BALANCE"));
    }

    @Test
    void should_returnRejectedWithoutCode_when_rejectionBodyNotErrorResponse() {
        // given
        server.expect(requestTo(POSTINGS_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.TEXT_HTML)
                        .body("<html>Bad request</html>"));

        // when
        var outcome = accountClient.submitPosting(TRANSFER_ID, "Chuyen tien", LINES);

        // then
        assertThat(outcome).isEqualTo(PostingOutcome.rejected(null));
    }

    @Test
    void should_throwAccountServiceUnavailable_when_postingTimesOut() {
        // given
        server.expect(requestTo(POSTINGS_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        // when
        var thrown = catchThrowable(() -> accountClient.submitPosting(TRANSFER_ID, "Chuyen tien", LINES));

        // then
        assertUnavailable(thrown);
    }

    @Test
    void should_throwAccountServiceUnavailable_when_postingReturns5xx() {
        // given: 503 ACCOUNT_BUSY hoac loi khong xac dinh, chua biet journal da ghi chua
        server.expect(requestTo(POSTINGS_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        // when
        var thrown = catchThrowable(() -> accountClient.submitPosting(TRANSFER_ID, "Chuyen tien", LINES));

        // then
        assertUnavailable(thrown);
    }

    @Test
    void should_throwAccountServiceUnavailable_when_postingReturnsTransient409() {
        // given: xung dot khi sua dong thoi, account-service da rollback, goi lai duoc
        server.expect(requestTo(POSTINGS_URL)).andRespond(withStatus(HttpStatus.CONFLICT));

        // when
        var thrown = catchThrowable(() -> accountClient.submitPosting(TRANSFER_ID, "Chuyen tien", LINES));

        // then
        assertUnavailable(thrown);
    }

    private static void assertUnavailable(Throwable thrown) {
        assertThat(thrown)
                .isInstanceOf(TechnicalException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.ACCOUNT_SERVICE_UNAVAILABLE);
    }
}
