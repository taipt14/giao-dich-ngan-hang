package vn.bank.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.bank.common.error.TechnicalException;
import vn.bank.transfer.domain.NewTransfer;
import vn.bank.transfer.domain.PostingDirection;
import vn.bank.transfer.domain.PostingLine;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferErrorCode;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.infrastructure.client.AccountClient;
import vn.bank.transfer.infrastructure.client.PostingOutcome;

class ExecuteInternalTransferServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private static final String TRANSFER_ID = "7f1c2a9e-1d2b-4c3d-9e8f-0a1b2c3d4e5f";
    private static final String SOURCE = "100000000001";
    private static final String DEST = "100000000002";
    private static final String DESCRIPTION = "Chuyen tien";
    private static final List<PostingLine> LINES = List.of(
            new PostingLine(SOURCE, PostingDirection.DEBIT, new BigDecimal("300000.00")),
            new PostingLine(DEST, PostingDirection.CREDIT, new BigDecimal("300000.00")));
    // Lan goi dau + 3 lan retry
    private static final int MAX_ATTEMPTS = 4;

    private final AccountClient accountClient = mock(AccountClient.class);
    private final PostingTransferStatusService statusService = mock(PostingTransferStatusService.class);
    private final TransferLimitService transferLimitService = mock(TransferLimitService.class);
    private final Retry retry = Retry.of(
            "test",
            RetryConfig.custom()
                    .maxAttempts(MAX_ATTEMPTS)
                    .waitDuration(Duration.ofMillis(1))
                    .retryExceptions(TechnicalException.class)
                    .build());
    private final ExecuteInternalTransferService service =
            new ExecuteInternalTransferService(accountClient, retry, statusService, transferLimitService);

    @Test
    void should_postTransferReferenceAndSucceed_when_postingRecorded() {
        // given
        when(accountClient.submitPosting(TRANSFER_ID, DESCRIPTION, LINES)).thenReturn(PostingOutcome.success());
        var succeeded = processingTransfer();
        when(statusService.markSucceeded(TRANSFER_ID)).thenReturn(succeeded);

        // when
        var result = service.execute(processingTransfer());

        // then
        assertThat(result).isSameAs(succeeded);
        verify(accountClient).submitPosting(TRANSFER_ID, DESCRIPTION, LINES);
        verify(transferLimitService, never()).release(any(Transfer.class));
    }

    @Test
    void should_failWithInsufficientBalanceAndReleaseLimitAfterCommit_when_postingRejected() {
        // given
        when(accountClient.submitPosting(TRANSFER_ID, DESCRIPTION, LINES))
                .thenReturn(PostingOutcome.rejected("INSUFFICIENT_BALANCE"));
        var failed = processingTransfer();
        when(statusService.markFailed(TRANSFER_ID, TransferErrorCode.INSUFFICIENT_BALANCE))
                .thenReturn(failed);

        // when
        var result = service.execute(processingTransfer());

        // then: hoan han muc sau khi FAILED da ghi
        assertThat(result).isSameAs(failed);
        var inOrder = inOrder(statusService, transferLimitService);
        inOrder.verify(statusService).markFailed(TRANSFER_ID, TransferErrorCode.INSUFFICIENT_BALANCE);
        inOrder.verify(transferLimitService).release(failed);
        verify(accountClient, times(1)).submitPosting(anyString(), anyString(), any());
    }

    @Test
    void should_failWithAccountNotActive_when_postingRejectedForLockedAccount() {
        // given
        when(accountClient.submitPosting(TRANSFER_ID, DESCRIPTION, LINES))
                .thenReturn(PostingOutcome.rejected("ACCOUNT_NOT_ACTIVE"));

        // when
        service.execute(processingTransfer());

        // then
        verify(statusService).markFailed(TRANSFER_ID, TransferErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    void should_failWithPostingRejected_when_rejectionCodeUnexpectedOrMissing() {
        // given
        when(accountClient.submitPosting(TRANSFER_ID, DESCRIPTION, LINES))
                .thenReturn(PostingOutcome.rejected("JOURNAL_NOT_BALANCED"), PostingOutcome.rejected(null));

        // when
        service.execute(processingTransfer());
        service.execute(processingTransfer());

        // then
        verify(statusService, times(2)).markFailed(TRANSFER_ID, TransferErrorCode.POSTING_REJECTED);
    }

    @Test
    void should_retryAndSucceed_when_technicalErrorThenPostingRecorded() {
        // given: lan dau timeout, lan retry account-service tra journal (idempotent)
        when(accountClient.submitPosting(TRANSFER_ID, DESCRIPTION, LINES))
                .thenThrow(timeout())
                .thenReturn(PostingOutcome.success());

        // when
        service.execute(processingTransfer());

        // then
        verify(accountClient, times(2)).submitPosting(TRANSFER_ID, DESCRIPTION, LINES);
        verify(statusService).markSucceeded(TRANSFER_ID);
    }

    @Test
    void should_markUnknownWithoutFailingOrReleasing_when_technicalErrorAfterThreeRetries() {
        // given
        when(accountClient.submitPosting(TRANSFER_ID, DESCRIPTION, LINES)).thenThrow(timeout());
        var unknown = processingTransfer();
        when(statusService.markUnknown(TRANSFER_ID)).thenReturn(unknown);

        // when
        var result = service.execute(processingTransfer());

        // then: chua chac tien chua bi tru nen khong FAILED, han muc van giu
        assertThat(result).isSameAs(unknown);
        verify(accountClient, times(MAX_ATTEMPTS)).submitPosting(TRANSFER_ID, DESCRIPTION, LINES);
        verify(statusService, never()).markFailed(anyString(), any(TransferErrorCode.class));
        verify(transferLimitService, never()).release(any(Transfer.class));
    }

    private static TechnicalException timeout() {
        return new TechnicalException(
                TransferErrorCode.ACCOUNT_SERVICE_UNAVAILABLE, "Read timed out", new RuntimeException("timeout"));
    }

    private static Transfer processingTransfer() {
        var request = new NewTransfer(
                "C000000001",
                "0b9f3c1e-5a7d-4e2f-8c6b-1d2e3f4a5b6c",
                SOURCE,
                DEST,
                null,
                new BigDecimal("300000"),
                BigDecimal.ZERO,
                DESCRIPTION);
        var transfer = Transfer.initiate(TRANSFER_ID, request, CLOCK);
        transfer.changeStatus(TransferStatus.OTP_PENDING, CLOCK);
        transfer.changeStatus(TransferStatus.PROCESSING, CLOCK);
        return transfer;
    }
}
