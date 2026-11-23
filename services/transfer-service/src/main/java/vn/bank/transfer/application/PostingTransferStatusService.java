package vn.bank.transfer.application;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.bank.common.error.BusinessException;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferErrorCode;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.infrastructure.persistence.TransferRepository;

/**
 * Ghi ket qua posting vao lenh, moi method mot transaction ngan. Tach khoi {@link ExecuteInternalTransferService} de
 * loi goi REST sang account-service nam ngoai transaction DB (muc 8 convention).
 */
@Service
@RequiredArgsConstructor
class PostingTransferStatusService {

    private static final int WRITE_TIMEOUT_SECONDS = 10;

    private final TransferRepository transferRepository;
    private final Clock clock;

    /** Posting da ghi: PROCESSING → SUCCESS. */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public Transfer markSucceeded(String transferId) {
        var transfer = getTransfer(transferId);
        transfer.changeStatus(TransferStatus.SUCCESS, clock);
        return transfer;
    }

    /** account-service tu choi posting (tien chua bi tru): PROCESSING → FAILED kem {@code failureCode}. */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public Transfer markFailed(String transferId, TransferErrorCode failureCode) {
        var transfer = getTransfer(transferId);
        transfer.fail(failureCode.code(), clock);
        return transfer;
    }

    /** Het retry ma chua biet posting da ghi hay chua: PROCESSING → UNKNOWN, giu nguyen han muc da giu. */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public Transfer markUnknown(String transferId) {
        var transfer = getTransfer(transferId);
        transfer.changeStatus(TransferStatus.UNKNOWN, clock);
        return transfer;
    }

    private Transfer getTransfer(String transferId) {
        return transferRepository
                .findById(transferId)
                .orElseThrow(() -> new BusinessException(
                        TransferErrorCode.TRANSFER_NOT_FOUND, "Transfer not found: " + transferId));
    }
}
