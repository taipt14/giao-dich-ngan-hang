package vn.bank.transfer.application;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.bank.common.error.BusinessException;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferErrorCode;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.infrastructure.messaging.TransferFailedProducer;
import vn.bank.transfer.infrastructure.persistence.TransferRepository;

/**
 * Doc va doi trang thai lenh trong luong OTP, moi method mot transaction ngan. Tach khoi {@link ConfirmOtpService} va
 * {@link ResendOtpService} de thao tac Redis nam ngoai transaction DB, va de goi qua proxy (khong self-invocation).
 * Lenh FAILED ghi {@code TransferFailed} vao outbox trong cung transaction (BANK-046).
 */
@Service
@RequiredArgsConstructor
class OtpTransferStatusService {

    private static final int WRITE_TIMEOUT_SECONDS = 10;

    private final TransferRepository transferRepository;
    private final TransferFailedProducer transferFailedProducer;
    private final Clock clock;

    /**
     * Lenh {@code transferId} cua khach hang {@code cif} dang cho OTP. Khong ton tai hoac cua khach hang khac →
     * {@code TRANSFER_NOT_FOUND}; khong o OTP_PENDING → {@code TRANSFER_NOT_AWAITING_OTP}.
     */
    @Transactional(readOnly = true)
    public Transfer getAwaitingOtp(String transferId, String cif) {
        var transfer = transferRepository
                .findById(transferId)
                .filter(found -> found.isOwnedBy(cif))
                .orElseThrow(() -> new BusinessException(
                        TransferErrorCode.TRANSFER_NOT_FOUND, "Transfer not found: " + transferId));
        transfer.requireAwaitingOtp();
        return transfer;
    }

    /** OTP dung: OTP_PENDING → PROCESSING. */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public Transfer markOtpVerified(String transferId) {
        var transfer = getTransfer(transferId);
        transfer.requireAwaitingOtp();
        transfer.changeStatus(TransferStatus.PROCESSING, clock);
        return transfer;
    }

    /** Sai OTP den so lan toi da: OTP_PENDING → FAILED ({@code OTP_MAX_ATTEMPTS}); lenh da FAILED thi giu nguyen. */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public void failByOtpMaxAttempts(String transferId) {
        failIfAwaitingOtp(transferId, TransferErrorCode.OTP_MAX_ATTEMPTS);
    }

    /** OTP dung nhung vuot han muc ngay: OTP_PENDING → FAILED ({@code DAILY_LIMIT_EXCEEDED}); da FAILED thi giu nguyen. */
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public void failByDailyLimitExceeded(String transferId) {
        failIfAwaitingOtp(transferId, TransferErrorCode.DAILY_LIMIT_EXCEEDED);
    }

    private void failIfAwaitingOtp(String transferId, TransferErrorCode failureCode) {
        var transfer = getTransfer(transferId);
        if (transfer.isAwaitingOtp()) {
            transfer.fail(failureCode.code(), clock);
            transferFailedProducer.send(transfer);
        }
    }

    private Transfer getTransfer(String transferId) {
        return transferRepository
                .findById(transferId)
                .orElseThrow(() -> new BusinessException(
                        TransferErrorCode.TRANSFER_NOT_FOUND, "Transfer not found: " + transferId));
    }
}
