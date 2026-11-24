package vn.bank.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.bank.transfer.domain.NewTransfer;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.infrastructure.messaging.TransferFailedProducer;
import vn.bank.transfer.infrastructure.persistence.TransferRepository;

class OtpTransferStatusServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private static final String TRANSFER_ID = "TRF-OTP-STATUS";

    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final TransferFailedProducer failedProducer = mock(TransferFailedProducer.class);
    private final OtpTransferStatusService service =
            new OtpTransferStatusService(transferRepository, failedProducer, CLOCK);

    @Test
    void should_writeTransferFailedToOutbox_when_otpWrongTooManyTimes() {
        // given
        var transfer = givenTransferIn(TransferStatus.OTP_PENDING);

        // when
        service.failByOtpMaxAttempts(TRANSFER_ID);

        // then
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getFailureCode()).isEqualTo("OTP_MAX_ATTEMPTS");
        verify(failedProducer).send(transfer);
    }

    @Test
    void should_writeTransferFailedToOutbox_when_dailyLimitExceededAtConfirm() {
        // given
        var transfer = givenTransferIn(TransferStatus.OTP_PENDING);

        // when
        service.failByDailyLimitExceeded(TRANSFER_ID);

        // then
        assertThat(transfer.getFailureCode()).isEqualTo("DAILY_LIMIT_EXCEEDED");
        verify(failedProducer).send(transfer);
    }

    @Test
    void should_notWriteEventAgain_when_transferNoLongerAwaitingOtp() {
        // given
        givenTransferIn(TransferStatus.PROCESSING);

        // when
        service.failByOtpMaxAttempts(TRANSFER_ID);

        // then
        verify(failedProducer, never()).send(any());
    }

    private Transfer givenTransferIn(TransferStatus status) {
        var request = new NewTransfer(
                "C000000001",
                "idem-key",
                "100000000001",
                "100000000002",
                null,
                new BigDecimal("300000"),
                BigDecimal.ZERO,
                "Chuyen tien");
        var transfer = Transfer.initiate(TRANSFER_ID, request, CLOCK);
        transfer.changeStatus(TransferStatus.OTP_PENDING, CLOCK);
        if (status == TransferStatus.PROCESSING) {
            transfer.changeStatus(TransferStatus.PROCESSING, CLOCK);
        }
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        return transfer;
    }
}
