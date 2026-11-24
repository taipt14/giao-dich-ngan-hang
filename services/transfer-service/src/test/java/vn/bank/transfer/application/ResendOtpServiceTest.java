package vn.bank.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.bank.common.error.BusinessException;
import vn.bank.common.error.ErrorCode;
import vn.bank.transfer.domain.NewTransfer;
import vn.bank.transfer.domain.OtpHash;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferErrorCode;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.infrastructure.client.OtpSender;
import vn.bank.transfer.infrastructure.redis.OtpResendOutcome;
import vn.bank.transfer.infrastructure.redis.OtpStore;

class ResendOtpServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private static final String TRANSFER_ID = "7f1c2a9e-1d2b-4c3d-9e8f-0a1b2c3d4e5f";
    private static final String CIF = "C000000001";
    private static final String OTP = "731508";
    private static final String OTP_HASH = OtpHash.of(TRANSFER_ID, OTP);

    private final OtpTransferStatusService otpTransferStatusService = mock(OtpTransferStatusService.class);
    private final OtpGenerator otpGenerator = mock(OtpGenerator.class);
    private final OtpStore otpStore = mock(OtpStore.class);
    private final OtpSender otpSender = mock(OtpSender.class);
    private final ResendOtpService service =
            new ResendOtpService(otpTransferStatusService, otpGenerator, otpStore, otpSender);

    @BeforeEach
    void setUp() {
        when(otpGenerator.generate()).thenReturn(OTP);
    }

    @Test
    void should_replaceOtpAndSendIt_when_resendAllowed() {
        // given
        givenAwaitingOtp();
        when(otpStore.resend(TRANSFER_ID, OTP_HASH)).thenReturn(OtpResendOutcome.RESENT);

        // when
        var transfer = service.resend(TRANSFER_ID, CIF);

        // then: van OTP_PENDING, OTP moi chi di qua OtpSender
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.OTP_PENDING);
        verify(otpSender).send(TRANSFER_ID, OTP);
    }

    @Test
    void should_rejectWithOtpResendTooSoon_when_resentWithinInterval() {
        // given
        givenAwaitingOtp();
        when(otpStore.resend(TRANSFER_ID, OTP_HASH)).thenReturn(OtpResendOutcome.TOO_SOON);

        // when
        var thrown = catchThrowable(() -> service.resend(TRANSFER_ID, CIF));

        // then
        assertErrorCode(thrown, TransferErrorCode.OTP_RESEND_TOO_SOON);
        verify(otpSender, never()).send(anyString(), anyString());
    }

    @Test
    void should_rejectWithOtpResendLimitExceeded_when_resentThreeTimesAlready() {
        // given
        givenAwaitingOtp();
        when(otpStore.resend(TRANSFER_ID, OTP_HASH)).thenReturn(OtpResendOutcome.LIMIT_EXCEEDED);

        // when
        var thrown = catchThrowable(() -> service.resend(TRANSFER_ID, CIF));

        // then
        assertErrorCode(thrown, TransferErrorCode.OTP_RESEND_LIMIT_EXCEEDED);
        verify(otpSender, never()).send(anyString(), anyString());
    }

    @Test
    void should_rejectWithoutTouchingOtp_when_transferNotAwaitingOtp() {
        // given
        when(otpTransferStatusService.getAwaitingOtp(TRANSFER_ID, CIF))
                .thenThrow(new BusinessException(TransferErrorCode.TRANSFER_NOT_AWAITING_OTP, "PROCESSING"));

        // when
        var thrown = catchThrowable(() -> service.resend(TRANSFER_ID, CIF));

        // then
        assertErrorCode(thrown, TransferErrorCode.TRANSFER_NOT_AWAITING_OTP);
        verify(otpStore, never()).resend(anyString(), anyString());
    }

    private void givenAwaitingOtp() {
        var request = new NewTransfer(
                CIF,
                "0b9f3c1e-5a7d-4e2f-8c6b-1d2e3f4a5b6c",
                "100000000001",
                "100000000002",
                null,
                new BigDecimal("500000"),
                BigDecimal.ZERO,
                "Chuyen tien");
        var transfer = Transfer.initiate(TRANSFER_ID, request, CLOCK);
        transfer.changeStatus(TransferStatus.OTP_PENDING, CLOCK);
        when(otpTransferStatusService.getAwaitingOtp(TRANSFER_ID, CIF)).thenReturn(transfer);
    }

    private static void assertErrorCode(Throwable thrown, ErrorCode code) {
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }
}
