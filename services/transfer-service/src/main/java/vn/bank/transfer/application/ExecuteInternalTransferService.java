package vn.bank.transfer.application;

import io.github.resilience4j.retry.Retry;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.bank.common.error.TechnicalException;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferErrorCode;
import vn.bank.transfer.infrastructure.client.AccountClient;
import vn.bank.transfer.infrastructure.client.PostingOutcome;

/**
 * Thuc hien lenh noi bo da xac thuc OTP (PROCESSING): ghi journal TRANSFER o account-service (No nguon / Co dich), roi
 * ghi ket qua vao lenh. Da ghi → SUCCESS; bi tu choi → FAILED va hoan han muc ngay; loi ky thuat → retry (an toan vi
 * posting idempotent theo transferId), het retry → UNKNOWN cho tra soat bang {@code GET /internal/postings}. Khong bao
 * gio FAILED khi chua chac tien chua bi tru. Khong mo transaction: REST nam ngoai transaction DB (muc 8 convention).
 */
@Slf4j
@Service
@RequiredArgsConstructor
class ExecuteInternalTransferService {

    // Ma loi nghiep vu cua account-service → FAILURE_CODE cua lenh; ma khac → POSTING_REJECTED
    private static final Map<String, TransferErrorCode> FAILURE_CODE_BY_REJECTION = Map.of(
            TransferErrorCode.INSUFFICIENT_BALANCE.code(), TransferErrorCode.INSUFFICIENT_BALANCE,
            TransferErrorCode.ACCOUNT_NOT_ACTIVE.code(), TransferErrorCode.ACCOUNT_NOT_ACTIVE);

    private final AccountClient accountClient;
    private final Retry postingRetry;
    private final PostingTransferStatusService postingTransferStatusService;
    private final TransferLimitService transferLimitService;

    /** {@code transfer}: lenh noi bo o PROCESSING, da commit. Tra lai lenh o SUCCESS, FAILED hoac UNKNOWN. */
    public Transfer execute(Transfer transfer) {
        var transferId = transfer.getTransferId();
        var lines = transfer.toInternalPostingLines();
        PostingOutcome outcome;
        try {
            outcome = postingRetry.executeSupplier(
                    () -> accountClient.submitPosting(transferId, transfer.getDescription(), lines));
        } catch (TechnicalException e) {
            log.error("Transfer {} UNKNOWN: posting result unknown after retries, needs inquiry", transferId, e);
            return postingTransferStatusService.markUnknown(transferId);
        }
        if (outcome.posted()) {
            var succeeded = postingTransferStatusService.markSucceeded(transferId);
            log.info("Transfer {} SUCCESS", transferId);
            return succeeded;
        }
        return fail(transferId, outcome.rejectionCode());
    }

    // Han muc chi hoan sau khi FAILED da commit (BANK-043): hoan truoc ma ghi FAILED loi thi han muc bi tinh thieu
    private Transfer fail(String transferId, String rejectionCode) {
        // Map.of khong nhan key null: body loi khong doc duoc thi khong co ma
        var failureCode = rejectionCode == null ? null : FAILURE_CODE_BY_REJECTION.get(rejectionCode);
        if (failureCode == null) {
            log.error("Posting of transfer {} rejected with unexpected code {}", transferId, rejectionCode);
            failureCode = TransferErrorCode.POSTING_REJECTED;
        }
        var failed = postingTransferStatusService.markFailed(transferId, failureCode);
        log.info("Transfer {} FAILED: {}", transferId, failureCode.code());
        transferLimitService.release(failed);
        return failed;
    }
}
