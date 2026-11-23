package vn.bank.transfer.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.bank.common.error.BusinessException;
import vn.bank.transfer.domain.OtpHash;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferErrorCode;
import vn.bank.transfer.infrastructure.redis.OtpStore;
import vn.bank.transfer.infrastructure.redis.OtpVerification;

/**
 * Khach hang xac thuc lenh bang OTP. Dung → OTP bi xoa (dung mot lan), giu cho han muc ngay roi lenh sang PROCESSING;
 * vuot han muc ngay → lenh FAILED ({@code DAILY_LIMIT_EXCEEDED}). Sai → tang bo dem; sai den so lan toi da → lenh
 * FAILED ({@code OTP_MAX_ATTEMPTS}), nhap dung sau do van bi tu choi vi lenh khong con cho OTP. Thao tac Redis nam
 * ngoai transaction DB (muc 8 convention).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmOtpService {

    private final OtpTransferStatusService otpTransferStatusService;
    private final OtpStore otpStore;
    private final TransferLimitService transferLimitService;

    /**
     * Lenh khong ton tai / cua khach hang khac → {@code TRANSFER_NOT_FOUND}; khong o OTP_PENDING →
     * {@code TRANSFER_NOT_AWAITING_OTP}; OTP het han → {@code OTP_EXPIRED}; sai → {@code OTP_INVALID}; sai lan cuoi →
     * {@code OTP_MAX_ATTEMPTS}; OTP dung nhung vuot han muc ngay → {@code DAILY_LIMIT_EXCEEDED}.
     */
    public Transfer confirm(String transferId, String cif, String otp) {
        var awaiting = otpTransferStatusService.getAwaitingOtp(transferId, cif);
        var verification = otpStore.verify(transferId, OtpHash.of(transferId, otp));
        return switch (verification.outcome()) {
            case VERIFIED -> verified(awaiting);
            case EXPIRED ->
                throw new BusinessException(
                        TransferErrorCode.OTP_EXPIRED, "OTP of transfer " + transferId + " has expired");
            case MISMATCH -> throw invalid(transferId, verification);
            case MAX_ATTEMPTS_REACHED -> throw maxAttemptsReached(transferId);
        };
    }

    // Giu cho han muc truoc khi sang PROCESSING: lenh PROCESSING luon da giu cho (tinh lai tu DB dua tren dieu nay)
    private Transfer verified(Transfer awaiting) {
        var transferId = awaiting.getTransferId();
        var reservation = transferLimitService.tryReserve(awaiting);
        if (reservation.isEmpty()) {
            throw dailyLimitExceeded(transferId);
        }
        var completed = false;
        try {
            var transfer = otpTransferStatusService.markOtpVerified(transferId);
            completed = true;
            log.info("Transfer {} OTP verified, status {}", transferId, transfer.getStatus());
            return transfer;
        } finally {
            if (!completed) {
                transferLimitService.cancel(reservation.get());
            }
        }
    }

    private static BusinessException invalid(String transferId, OtpVerification verification) {
        log.warn("Wrong OTP for transfer {}, failed attempts {}", transferId, verification.failedAttempts());
        return new BusinessException(
                TransferErrorCode.OTP_INVALID, "Wrong OTP, failed attempts: " + verification.failedAttempts());
    }

    private BusinessException maxAttemptsReached(String transferId) {
        otpTransferStatusService.failByOtpMaxAttempts(transferId);
        otpStore.discard(transferId);
        log.info("Transfer {} failed: OTP max attempts reached", transferId);
        return new BusinessException(
                TransferErrorCode.OTP_MAX_ATTEMPTS, "Too many wrong OTP attempts, transfer " + transferId + " failed");
    }

    private BusinessException dailyLimitExceeded(String transferId) {
        otpTransferStatusService.failByDailyLimitExceeded(transferId);
        otpStore.discard(transferId);
        log.info("Transfer {} failed: daily limit exceeded", transferId);
        return new BusinessException(
                TransferErrorCode.DAILY_LIMIT_EXCEEDED,
                "Daily transfer limit exceeded, transfer " + transferId + " failed");
    }
}
