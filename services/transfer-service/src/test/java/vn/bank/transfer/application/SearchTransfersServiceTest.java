package vn.bank.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import vn.bank.common.error.BusinessException;
import vn.bank.transfer.domain.NewTransfer;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferErrorCode;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.infrastructure.persistence.TransferRepository;

class SearchTransfersServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private static final String CIF = "C000000001";

    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final SearchTransfersService service = new SearchTransfersService(transferRepository);

    @Test
    void should_queryOwnTransfersWithStatusAndVietnamDayBounds_when_filtersGiven() {
        // given
        var transfer = transfer();
        var pageable = PageRequest.of(1, 2);
        when(transferRepository.findPageOfCustomer(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(transfer), pageable, 3));
        var query = new TransferSearchQuery(
                CIF, TransferStatus.SUCCESS, LocalDate.parse("2026-11-20"), LocalDate.parse("2026-11-21"), 1, 2);

        // when
        var page = service.search(query);

        // then
        verify(transferRepository)
                .findPageOfCustomer(
                        CIF,
                        TransferStatus.SUCCESS,
                        Instant.parse("2026-11-19T17:00:00Z"),
                        Instant.parse("2026-11-21T17:00:00Z"),
                        pageable);
        assertThat(page.items()).containsExactly(transfer);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(3);
    }

    @Test
    void should_rejectWithTransferRangeInvalid_when_fromAfterTo() {
        // given
        var query =
                new TransferSearchQuery(CIF, null, LocalDate.parse("2026-11-21"), LocalDate.parse("2026-11-20"), 0, 20);

        // when
        var thrown = catchThrowable(() -> service.search(query));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.TRANSFER_RANGE_INVALID);
        verifyNoInteractions(transferRepository);
    }

    private static Transfer transfer() {
        var request = new NewTransfer(
                CIF,
                UUID.randomUUID().toString(),
                "100000000001",
                "100000000002",
                null,
                new BigDecimal("100000"),
                BigDecimal.ZERO,
                null);
        return Transfer.initiate(UUID.randomUUID().toString(), request, CLOCK);
    }
}
