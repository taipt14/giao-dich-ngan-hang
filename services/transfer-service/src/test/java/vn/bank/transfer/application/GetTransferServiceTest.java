package vn.bank.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.bank.common.error.BusinessException;
import vn.bank.transfer.domain.NewTransfer;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferErrorCode;
import vn.bank.transfer.infrastructure.persistence.TransferRepository;

class GetTransferServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private static final String TRANSFER_ID = "7f1c2a9e-1d2b-4c3d-9e8f-0a1b2c3d4e5f";
    private static final String CIF = "C000000001";
    private static final String OTHER_CIF = "C000000002";

    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final GetTransferService service = new GetTransferService(transferRepository);

    @Test
    void should_returnTransfer_when_ownedByCustomer() {
        // given
        var transfer = transferOf(CIF);
        when(transferRepository.findWithStatusHistoryById(TRANSFER_ID)).thenReturn(Optional.of(transfer));

        // when
        var found = service.getTransfer(TRANSFER_ID, CIF);

        // then
        assertThat(found).isSameAs(transfer);
    }

    @Test
    void should_rejectWithTransferNotFound_when_transferOfAnotherCustomer() {
        // given
        when(transferRepository.findWithStatusHistoryById(TRANSFER_ID)).thenReturn(Optional.of(transferOf(OTHER_CIF)));

        // when
        var thrown = catchThrowable(() -> service.getTransfer(TRANSFER_ID, CIF));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.TRANSFER_NOT_FOUND);
    }

    @Test
    void should_rejectWithTransferNotFound_when_transferMissing() {
        // given
        when(transferRepository.findWithStatusHistoryById(TRANSFER_ID)).thenReturn(Optional.empty());

        // when
        var thrown = catchThrowable(() -> service.getTransfer(TRANSFER_ID, CIF));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.TRANSFER_NOT_FOUND);
    }

    private static Transfer transferOf(String cif) {
        var request = new NewTransfer(
                cif,
                "0b6f3c1e-2a4d-4e5f-8a9b-1c2d3e4f5a6b",
                "100000000001",
                "100000000002",
                null,
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                null);
        return Transfer.initiate(TRANSFER_ID, request, CLOCK);
    }
}
