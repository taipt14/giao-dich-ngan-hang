package vn.bank.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.bank.common.error.BusinessException;
import vn.bank.common.error.ErrorCode;
import vn.bank.transfer.domain.NewTransfer;
import vn.bank.transfer.domain.OtpHash;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferErrorCode;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.infrastructure.redis.OtpStore;
import vn.bank.transfer.infrastructure.redis.OtpVerification;
import vn.bank.transfer.infrastructure.redis.OtpVerification.Outcome;

class ConfirmOtpServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private static final String TRANSFER_ID = "7f1c2a9e-1d2b-4c3d-9e8f-0a1b2c3d4e5f";
    private static final String CIF = "C000000001";
    private static final String OTP = "042917";
    private static final String OTP_HASH = OtpHash.of(TRANSFER_ID, OTP);

    private final OtpTransferStatusService otpTransferStatusService = mock(OtpTransferStatusService.class);
    private final OtpStore otpStore = mock(OtpStore.class);
    private final TransferLimitService transferLimitService = mock(TransferLimitService.class);
    private final ExecuteInternalTransferService executeInternalTransferService =
            mock(ExecuteInternalTransferService.class);
    private final ConfirmOtpService service = new ConfirmOtpService(
            otpTransferStatusService, otpStore, transferLimitService, executeInternalTransferService);

    @Test
    void should_executeInternalTransferAfterProcessing_when_otpCorrect() {
        // given
        var processing = transferIn(TransferStatus.PROCESSING);
        var executed = transferIn(TransferStatus.SUCCESS);
        givenAwaitingOtp();
        when(otpStore.verify(TRANSFER_ID, OTP_HASH)).thenReturn(new OtpVerification(Outcome.VERIFIED, 0));
        givenLimitReserved();
        when(otpTransferStatusService.markOtpVerified(TRANSFER_ID)).thenReturn(processing);
        when(executeInternalTransferService.execute(processing)).thenReturn(executed);

        // when
        var transfer = service.confirm(TRANSFER_ID, CIF, OTP);

        // then: posting chi chay sau khi PROCESSING da commit
        var inOrder = inOrder(otpTransferStatusService, executeInternalTransferService);
        inOrder.verify(otpTransferStatusService).markOtpVerified(TRANSFER_ID);
        inOrder.verify(executeInternalTransferService).execute(processing);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.SUCCESS);
        verify(transferLimitService, never()).cancel(any(DailyLimitReservation.class));
    }

    @Test
    void should_keepProcessingWithoutPosting_when_interbankTransferConfirmed() {
        // given: lien ngan hang di qua NAPAS (E5), khong posting noi bo
        var processing = interbankProcessingTransfer();
        when(otpTransferStatusService.getAwaitingOtp(TRANSFER_ID, CIF)).thenReturn(processing);
        when(otpStore.verify(TRANSFER_ID, OTP_HASH)).thenReturn(new OtpVerification(Outcome.VERIFIED, 0));
        givenLimitReserved();
        when(otpTransferStatusService.markOtpVerified(TRANSFER_ID)).thenReturn(processing);

        // when
        var transfer = service.confirm(TRANSFER_ID, CIF, OTP);

        // then
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PROCESSING);
        verify(executeInternalTransferService, never()).execute(any(Transfer.class));
    }

    @Test
    void should_failTransferWithDailyLimitExceeded_when_otpCorrectButDailyLimitUsedUp() {
        // given
        givenAwaitingOtp();
        when(otpStore.verify(TRANSFER_ID, OTP_HASH)).thenReturn(new OtpVerification(Outcome.VERIFIED, 0));
        when(transferLimitService.tryReserve(any(Transfer.class))).thenReturn(Optional.empty());

        // when
        var thrown = catchThrowable(() -> service.confirm(TRANSFER_ID, CIF, OTP));

        // then: lenh FAILED, khong sang PROCESSING
        assertErrorCode(thrown, TransferErrorCode.DAILY_LIMIT_EXCEEDED);
        verify(otpTransferStatusService).failByDailyLimitExceeded(TRANSFER_ID);
        verify(otpTransferStatusService, never()).markOtpVerified(anyString());
        verify(otpStore).discard(TRANSFER_ID);
    }

    @Test
    void should_cancelReservation_when_transferCannotMoveToProcessing() {
        // given
        givenAwaitingOtp();
        when(otpStore.verify(TRANSFER_ID, OTP_HASH)).thenReturn(new OtpVerification(Outcome.VERIFIED, 0));
        var reservation = givenLimitReserved();
        doThrow(new IllegalStateException("DB down"))
                .when(otpTransferStatusService)
                .markOtpVerified(TRANSFER_ID);

        // when
        var thrown = catchThrowable(() -> service.confirm(TRANSFER_ID, CIF, OTP));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(transferLimitService).cancel(reservation);
    }

    @Test
    void should_notReserveLimit_when_otpWrong() {
        // given
        givenAwaitingOtp();
        when(otpStore.verify(TRANSFER_ID, OTP_HASH)).thenReturn(new OtpVerification(Outcome.MISMATCH, 1));

        // when
        catchThrowable(() -> service.confirm(TRANSFER_ID, CIF, OTP));

        // then
        verify(transferLimitService, never()).tryReserve(any(Transfer.class));
    }

    @Test
    void should_rejectWithOtpExpired_when_otpNoLongerStored() {
        // given
        givenAwaitingOtp();
        when(otpStore.verify(TRANSFER_ID, OTP_HASH)).thenReturn(new OtpVerification(Outcome.EXPIRED, 0));

        // when
        var thrown = catchThrowable(() -> service.confirm(TRANSFER_ID, CIF, OTP));

        // then
        assertErrorCode(thrown, TransferErrorCode.OTP_EXPIRED);
        verify(otpTransferStatusService, never()).markOtpVerified(anyString());
    }

    @Test
    void should_rejectWithOtpInvalidAndKeepTransfer_when_otpWrong() {
        // given
        givenAwaitingOtp();
        when(otpStore.verify(TRANSFER_ID, OTP_HASH)).thenReturn(new OtpVerification(Outcome.MISMATCH, 2));

        // when
        var thrown = catchThrowable(() -> service.confirm(TRANSFER_ID, CIF, OTP));

        // then
        assertErrorCode(thrown, TransferErrorCode.OTP_INVALID);
        assertThat(thrown).hasMessageNotContaining(OTP);
        verify(otpTransferStatusService, never()).failByOtpMaxAttempts(anyString());
    }

    @Test
    void should_failTransferWithOtpMaxAttemptsAndDiscardOtp_when_fifthWrongOtp() {
        // given
        givenAwaitingOtp();
        when(otpStore.verify(TRANSFER_ID, OTP_HASH)).thenReturn(new OtpVerification(Outcome.MAX_ATTEMPTS_REACHED, 5));

        // when
        var thrown = catchThrowable(() -> service.confirm(TRANSFER_ID, CIF, OTP));

        // then
        assertErrorCode(thrown, TransferErrorCode.OTP_MAX_ATTEMPTS);
        verify(otpTransferStatusService).failByOtpMaxAttempts(TRANSFER_ID);
        verify(otpStore).discard(TRANSFER_ID);
    }

    @Test
    void should_rejectWithoutCheckingOtp_when_transferNotAwaitingOtp() {
        // given: lenh da FAILED / da xac thuc
        when(otpTransferStatusService.getAwaitingOtp(TRANSFER_ID, CIF))
                .thenThrow(new BusinessException(TransferErrorCode.TRANSFER_NOT_AWAITING_OTP, "FAILED"));

        // when
        var thrown = catchThrowable(() -> service.confirm(TRANSFER_ID, CIF, OTP));

        // then
        assertErrorCode(thrown, TransferErrorCode.TRANSFER_NOT_AWAITING_OTP);
        verify(otpStore, never()).verify(anyString(), anyString());
    }

    private DailyLimitReservation givenLimitReserved() {
        var reservation =
                new DailyLimitReservation("100000000001", LocalDate.of(2026, 11, 23), new BigDecimal("500000.00"));
        when(transferLimitService.tryReserve(any(Transfer.class))).thenReturn(Optional.of(reservation));
        return reservation;
    }

    private void givenAwaitingOtp() {
        when(otpTransferStatusService.getAwaitingOtp(TRANSFER_ID, CIF))
                .thenReturn(transferIn(TransferStatus.OTP_PENDING));
    }

    private static Transfer transferIn(TransferStatus status) {
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
        if (status == TransferStatus.PROCESSING || status == TransferStatus.SUCCESS) {
            transfer.changeStatus(TransferStatus.PROCESSING, CLOCK);
        }
        if (status == TransferStatus.SUCCESS) {
            transfer.changeStatus(TransferStatus.SUCCESS, CLOCK);
        }
        return transfer;
    }

    private static Transfer interbankProcessingTransfer() {
        var request = new NewTransfer(
                CIF,
                "0b9f3c1e-5a7d-4e2f-8c6b-1d2e3f4a5b6c",
                "100000000001",
                "9704000000001",
                "970436",
                new BigDecimal("500000"),
                new BigDecimal("2200"),
                "Chuyen tien");
        var transfer = Transfer.initiate(TRANSFER_ID, request, CLOCK);
        transfer.changeStatus(TransferStatus.OTP_PENDING, CLOCK);
        transfer.changeStatus(TransferStatus.PROCESSING, CLOCK);
        return transfer;
    }

    private static void assertErrorCode(Throwable thrown, ErrorCode code) {
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }
}
