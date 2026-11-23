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
 * Khach hang xac thuc lenh bang OTP. Dung → OTP bi xoa (dung mot lan), lenh sang PROCESSING. Sai → tang bo dem; sai den
 * so lan toi da → lenh FAILED ({@code OTP_MAX_ATTEMPTS}), nhap dung sau do van bi tu choi vi lenh khong con cho OTP.
 * Thao tac Redis nam ngoai transaction DB (muc 8 convention).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmOtpService {

    private final OtpTransferStatusService otpTransferStatusService;
    private final OtpStore otpStore;

    /**
     * Lenh khong ton tai / cua khach hang khac → {@code TRANSFER_NOT_FOUND}; khong o OTP_PENDING →
     * {@code TRANSFER_NOT_AWAITING_OTP}; OTP het han → {@code OTP_EXPIRED}; sai → {@code OTP_INVALID}; sai lan cuoi →
     * {@code OTP_MAX_ATTEMPTS}.
     */
    public Transfer confirm(String transferId, String cif, String otp) {
        otpTransferStatusService.getAwaitingOtp(transferId, cif);
        var verification = otpStore.verify(transferId, OtpHash.of(transferId, otp));
        return switch (verification.outcome()) {
            case VERIFIED -> verified(transferId);
            case EXPIRED ->
                throw new BusinessException(
                        TransferErrorCode.OTP_EXPIRED, "OTP of transfer " + transferId + " has expired");
            case MISMATCH -> throw invalid(transferId, verification);
            case MAX_ATTEMPTS_REACHED -> throw maxAttemptsReached(transferId);
        };
    }

    private Transfer verified(String transferId) {
        var transfer = otpTransferStatusService.markOtpVerified(transferId);
        log.info("Transfer {} OTP verified, status {}", transferId, transfer.getStatus());
        return transfer;
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
}
