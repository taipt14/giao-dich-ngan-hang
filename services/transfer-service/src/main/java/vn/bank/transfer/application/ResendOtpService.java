package vn.bank.transfer.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.bank.common.error.BusinessException;
import vn.bank.transfer.domain.OtpHash;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferErrorCode;
import vn.bank.transfer.infrastructure.client.OtpSender;
import vn.bank.transfer.infrastructure.redis.OtpStore;

/**
 * Gui lai OTP cho lenh dang OTP_PENDING: OTP moi thay OTP cu (OTP cu het hieu luc), so lan gui lai va khoang cach giua
 * hai lan gui do {@link OtpStore} kiem tra atomic. Trang thai lenh giu nguyen OTP_PENDING, khong ghi lich su.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResendOtpService {

    private final OtpTransferStatusService otpTransferStatusService;
    private final OtpGenerator otpGenerator;
    private final OtpStore otpStore;
    private final OtpSender otpSender;

    /**
     * Lenh khong ton tai / cua khach hang khac → {@code TRANSFER_NOT_FOUND}; khong o OTP_PENDING →
     * {@code TRANSFER_NOT_AWAITING_OTP}; chua du khoang cach → {@code OTP_RESEND_TOO_SOON}; het luot →
     * {@code OTP_RESEND_LIMIT_EXCEEDED}.
     */
    public Transfer resend(String transferId, String cif) {
        var transfer = otpTransferStatusService.getAwaitingOtp(transferId, cif);
        var otp = otpGenerator.generate();
        switch (otpStore.resend(transferId, OtpHash.of(transferId, otp))) {
            case RESENT -> otpSender.send(transferId, otp);
            case TOO_SOON ->
                throw new BusinessException(
                        TransferErrorCode.OTP_RESEND_TOO_SOON, "OTP was sent recently, please wait before resending");
            case LIMIT_EXCEEDED ->
                throw new BusinessException(
                        TransferErrorCode.OTP_RESEND_LIMIT_EXCEEDED,
                        "OTP resend limit reached for transfer " + transferId);
        }
        log.info("Transfer {} OTP resent", transferId);
        return transfer;
    }
}
