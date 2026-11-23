package vn.bank.transfer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import vn.bank.common.error.ErrorResponse;
import vn.bank.common.test.AbstractIntegrationTest;
import vn.bank.transfer.domain.NewTransfer;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.infrastructure.persistence.TransferRepository;

/**
 * {@code GET /api/v1/transfers} va {@code GET /api/v1/transfers/{id}} voi Oracle that. Lenh duoc ghi thang qua
 * repository voi CREATED_AT co dinh; container co the duoc dung lai giua cac lan chay nen moi test dung CIF rieng.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class TransferQueryIT extends AbstractIntegrationTest {

    private static final String TRANSFERS_PATH = "/api/v1/transfers";
    private static final String ROLES_HEADER = "X-Roles";
    private static final String CIF_HEADER = "X-Cif";
    private static final String CUSTOMER = "CUSTOMER";
    private static final String OTP_MAX_ATTEMPTS = "OTP_MAX_ATTEMPTS";
    private static final String DEST_ACCOUNT = "100012345678";
    private static final String MASKED_DEST_ACCOUNT = "1000****5678";

    private final TransferRepository transferRepository;
    private final TransactionTemplate transactionTemplate;

    @LocalServerPort
    private int port;

    private RestClient client;
    private String cif;

    TransferQueryIT(
            @Autowired TransferRepository transferRepository,
            @Autowired PlatformTransactionManager transactionManager) {
        this.transferRepository = transferRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        // Khong nem exception voi 4xx/5xx de test doc duoc body loi
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
                .build();
        cif = randomCif();
    }

    @Test
    void should_listOnlyOwnTransfers_when_otherCustomersHaveTransfers() {
        // given
        var own = givenTransfer(cif, "2026-11-20T03:00:00Z", TransferStatus.SUCCESS);
        givenTransfer(randomCif(), "2026-11-20T03:00:00Z", TransferStatus.SUCCESS);

        // when
        var response = list(cif, "", TransferPageResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items())
                .extracting(TransferSummaryResponse::transferId)
                .containsExactly(own);
        assertThat(response.getBody().totalElements()).isEqualTo(1);
    }

    @Test
    void should_return404TransferNotFound_when_gettingTransferOfAnotherCustomer() {
        // given
        var otherTransfer = givenTransfer(randomCif(), "2026-11-20T03:00:00Z", TransferStatus.SUCCESS);

        // when
        var response = get(cif, otherTransfer, ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("TRANSFER_NOT_FOUND");
    }

    @Test
    void should_returnOnlyMatchingStatus_when_filteredByStatus() {
        // given
        var success = givenTransfer(cif, "2026-11-20T03:00:00Z", TransferStatus.SUCCESS);
        givenTransfer(cif, "2026-11-20T04:00:00Z", TransferStatus.FAILED);
        givenTransfer(cif, "2026-11-20T05:00:00Z", TransferStatus.OTP_PENDING);

        // when
        var response = list(cif, "?status=SUCCESS", TransferPageResponse.class);

        // then
        assertThat(response.getBody().items())
                .extracting(TransferSummaryResponse::transferId, TransferSummaryResponse::status)
                .containsExactly(tuple(success, TransferStatus.SUCCESS));
    }

    @Test
    void should_includeWholeVietnamBusinessDays_when_filteredByDateRange() {
        // given: 20-21/11 gio Viet Nam = [2026-11-19T17:00Z, 2026-11-21T17:00Z)
        givenTransfer(cif, "2026-11-19T16:59:59Z", TransferStatus.SUCCESS);
        var firstInRange = givenTransfer(cif, "2026-11-19T17:00:00Z", TransferStatus.SUCCESS);
        var lastInRange = givenTransfer(cif, "2026-11-21T16:59:59Z", TransferStatus.FAILED);
        givenTransfer(cif, "2026-11-21T17:00:00Z", TransferStatus.SUCCESS);

        // when
        var response = list(cif, "?from=2026-11-20&to=2026-11-21", TransferPageResponse.class);

        // then: moi nhat truoc
        assertThat(response.getBody().items())
                .extracting(TransferSummaryResponse::transferId)
                .containsExactly(lastInRange, firstInRange);
        assertThat(response.getBody().totalElements()).isEqualTo(2);
    }

    @Test
    void should_applyStatusAndDateRangeTogether_when_bothFiltersGiven() {
        // given
        givenTransfer(cif, "2026-11-18T03:00:00Z", TransferStatus.FAILED);
        var match = givenTransfer(cif, "2026-11-20T03:00:00Z", TransferStatus.FAILED);
        givenTransfer(cif, "2026-11-20T04:00:00Z", TransferStatus.SUCCESS);

        // when
        var response = list(cif, "?status=FAILED&from=2026-11-20&to=2026-11-20", TransferPageResponse.class);

        // then
        assertThat(response.getBody().items())
                .extracting(TransferSummaryResponse::transferId, TransferSummaryResponse::failureCode)
                .containsExactly(tuple(match, OTP_MAX_ATTEMPTS));
    }

    @Test
    void should_return400TransferRangeInvalid_when_fromAfterTo() {
        // given / when
        var response = list(cif, "?from=2026-11-21&to=2026-11-20", ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("TRANSFER_RANGE_INVALID");
    }

    @Test
    void should_return400_when_statusUnknown() {
        // given / when
        var response = list(cif, "?status=DONE", ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_return400ValidationFailed_when_sizeAboveMaximum() {
        // given / when
        var response = list(cif, "?size=101", ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void should_pageNewestFirstWithTotalElements_when_moreTransfersThanSize() {
        // given
        var oldest = givenTransfer(cif, "2026-11-20T01:00:00Z", TransferStatus.SUCCESS);
        var middle = givenTransfer(cif, "2026-11-20T02:00:00Z", TransferStatus.SUCCESS);
        var newest = givenTransfer(cif, "2026-11-20T03:00:00Z", TransferStatus.SUCCESS);

        // when
        var first = list(cif, "?page=0&size=2", TransferPageResponse.class).getBody();
        var second = list(cif, "?page=1&size=2", TransferPageResponse.class).getBody();

        // then
        assertThat(first.items())
                .extracting(TransferSummaryResponse::transferId)
                .containsExactly(newest, middle);
        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(second.items())
                .extracting(TransferSummaryResponse::transferId)
                .containsExactly(oldest);
        assertThat(second.page()).isEqualTo(1);
    }

    @Test
    void should_useDefaultPageSize20_when_sizeMissing() {
        // given
        givenTransfer(cif, "2026-11-20T03:00:00Z", TransferStatus.SUCCESS);

        // when
        var body = list(cif, "", TransferPageResponse.class).getBody();

        // then
        assertThat(body.page()).isZero();
        assertThat(body.size()).isEqualTo(20);
    }

    @Test
    void should_maskDestAccountAndKeepMoneyScale_when_listing() {
        // given
        givenTransfer(cif, "2026-11-20T03:00:00Z", TransferStatus.SUCCESS);

        // when
        var item = list(cif, "", TransferPageResponse.class).getBody().items().getFirst();

        // then
        assertThat(item.destAccount()).isEqualTo(MASKED_DEST_ACCOUNT);
        assertThat(item.amount()).isEqualTo(new BigDecimal("250000.00"));
        assertThat(item.fee()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void should_returnDetailWithFullDestAccountAndStatusHistoryInOrder_when_gettingOwnTransfer() {
        // given
        var transferId = givenTransfer(cif, "2026-11-20T03:00:00Z", TransferStatus.FAILED);

        // when
        var response = get(cif, transferId, TransferDetailResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var detail = response.getBody();
        assertThat(detail.transferId()).isEqualTo(transferId);
        assertThat(detail.status()).isEqualTo(TransferStatus.FAILED);
        assertThat(detail.failureCode()).isEqualTo(OTP_MAX_ATTEMPTS);
        assertThat(detail.destAccount()).isEqualTo(DEST_ACCOUNT);
        assertThat(detail.amount()).isEqualTo(new BigDecimal("250000.00"));
        assertThat(detail.createdAt()).isEqualTo(Instant.parse("2026-11-20T03:00:00Z"));
        assertThat(detail.statusHistory())
                .extracting(
                        TransferStatusHistoryResponse::previousStatus,
                        TransferStatusHistoryResponse::status,
                        TransferStatusHistoryResponse::failureCode)
                .containsExactly(
                        tuple(null, TransferStatus.INITIATED, null),
                        tuple(TransferStatus.INITIATED, TransferStatus.OTP_PENDING, null),
                        tuple(TransferStatus.OTP_PENDING, TransferStatus.FAILED, OTP_MAX_ATTEMPTS));
    }

    @Test
    void should_return403_when_userIsNotCustomer() {
        // given / when
        var response = client.get()
                .uri(TRANSFERS_PATH)
                .header(CIF_HEADER, cif)
                .header(ROLES_HEADER, "ADMIN")
                .retrieve()
                .toEntity(ErrorResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** Tao lenh cua {@code ownerCif} tai {@code createdAt} roi dua den {@code status} (FAILED: sai OTP qua so lan). */
    private String givenTransfer(String ownerCif, String createdAt, TransferStatus status) {
        var clock = Clock.fixed(Instant.parse(createdAt), ZoneOffset.UTC);
        var transferId = UUID.randomUUID().toString();
        var request = new NewTransfer(
                ownerCif,
                UUID.randomUUID().toString(),
                "100000000001",
                DEST_ACCOUNT,
                null,
                new BigDecimal("250000"),
                BigDecimal.ZERO,
                "Chuyen tien IT");
        transactionTemplate.executeWithoutResult(tx -> {
            var transfer = Transfer.initiate(transferId, request, clock);
            transfer.changeStatus(TransferStatus.OTP_PENDING, clock);
            moveTo(transfer, status, clock);
            transferRepository.save(transfer);
        });
        return transferId;
    }

    private static void moveTo(Transfer transfer, TransferStatus status, Clock clock) {
        switch (status) {
            case OTP_PENDING -> {}
            case FAILED -> transfer.fail(OTP_MAX_ATTEMPTS, clock);
            case SUCCESS -> {
                transfer.changeStatus(TransferStatus.PROCESSING, clock);
                transfer.changeStatus(TransferStatus.SUCCESS, clock);
            }
            default -> throw new IllegalArgumentException("Unsupported test status " + status);
        }
    }

    private <T> ResponseEntity<T> list(String customerCif, String queryString, Class<T> responseType) {
        return client.get()
                .uri(TRANSFERS_PATH + queryString)
                .header(ROLES_HEADER, CUSTOMER)
                .header(CIF_HEADER, customerCif)
                .retrieve()
                .toEntity(responseType);
    }

    private <T> ResponseEntity<T> get(String customerCif, String transferId, Class<T> responseType) {
        return client.get()
                .uri(TRANSFERS_PATH + "/{transferId}", transferId)
                .header(ROLES_HEADER, CUSTOMER)
                .header(CIF_HEADER, customerCif)
                .retrieve()
                .toEntity(responseType);
    }

    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }
}
