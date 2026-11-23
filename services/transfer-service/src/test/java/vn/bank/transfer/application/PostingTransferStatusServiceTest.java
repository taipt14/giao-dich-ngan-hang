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
import vn.bank.transfer.domain.TransferErrorCode;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.infrastructure.messaging.TransferCompletedProducer;
import vn.bank.transfer.infrastructure.messaging.TransferFailedProducer;
import vn.bank.transfer.infrastructure.persistence.TransferRepository;

class PostingTransferStatusServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private static final String TRANSFER_ID = "TRF-POSTING-STATUS";

    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final TransferCompletedProducer completedProducer = mock(TransferCompletedProducer.class);
    private final TransferFailedProducer failedProducer = mock(TransferFailedProducer.class);
    private final PostingTransferStatusService service =
            new PostingTransferStatusService(transferRepository, completedProducer, failedProducer, CLOCK);

    @Test
    void should_writeTransferCompletedToOutbox_when_transferSucceeded() {
        // given
        var transfer = givenProcessingTransfer();

        // when
        service.markSucceeded(TRANSFER_ID);

        // then
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.SUCCESS);
        verify(completedProducer).send(transfer);
        verify(failedProducer, never()).send(any());
    }

    @Test
    void should_writeTransferFailedToOutbox_when_postingRejected() {
        // given
        var transfer = givenProcessingTransfer();

        // when
        service.markFailed(TRANSFER_ID, TransferErrorCode.INSUFFICIENT_BALANCE);

        // then
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        verify(failedProducer).send(transfer);
        verify(completedProducer, never()).send(any());
    }

    @Test
    void should_notWriteAnyEvent_when_transferUnknown() {
        // given
        givenProcessingTransfer();

        // when
        service.markUnknown(TRANSFER_ID);

        // then: chua biet ket qua, TransferCompleted/TransferFailed phat khi tra soat xong (BANK-054)
        verify(completedProducer, never()).send(any());
        verify(failedProducer, never()).send(any());
    }

    private Transfer givenProcessingTransfer() {
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
        transfer.changeStatus(TransferStatus.PROCESSING, CLOCK);
        when(transferRepository.findById(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        return transfer;
    }
}
