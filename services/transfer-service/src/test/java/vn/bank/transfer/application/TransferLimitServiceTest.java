package vn.bank.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.bank.common.error.BusinessException;
import vn.bank.common.error.ErrorCode;
import vn.bank.transfer.domain.CustomerType;
import vn.bank.transfer.domain.NewTransfer;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferErrorCode;
import vn.bank.transfer.domain.TransferLimitConfig;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.infrastructure.persistence.TransferLimitConfigRepository;
import vn.bank.transfer.infrastructure.persistence.TransferRepository;
import vn.bank.transfer.infrastructure.redis.LimitReservationOutcome;
import vn.bank.transfer.infrastructure.redis.TransferLimitStore;

class TransferLimitServiceTest {

    // 2026-11-23 17:30 UTC = 2026-11-24 00:30 gio Viet Nam: ngay nghiep vu da sang 24/11
    private static final Instant NOW = Instant.parse("2026-11-23T17:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final LocalDate BUSINESS_DAY = LocalDate.of(2026, 11, 24);
    private static final String SOURCE = "100000000001";
    private static final BigDecimal DAILY_LIMIT = new BigDecimal("500000000.00");

    private final TransferLimitConfigRepository configRepository = mock(TransferLimitConfigRepository.class);
    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final TransferLimitStore store = mock(TransferLimitStore.class);
    private final TransferLimitService service =
            new TransferLimitService(configRepository, transferRepository, store, CLOCK);

    @BeforeEach
    void givenStandardLimit() {
        when(configRepository.findById(CustomerType.STANDARD))
                .thenReturn(Optional.of(
                        TransferLimitConfig.of(CustomerType.STANDARD, new BigDecimal("100000000.00"), DAILY_LIMIT)));
    }

    @Test
    void should_rejectWithPerTransactionLimitExceeded_when_creatingTransferOf100000001() {
        // when
        var thrown = catchThrowable(() -> service.checkCanCreate(request("100000001")));

        // then
        assertErrorCode(thrown, TransferErrorCode.PER_TRANSACTION_LIMIT_EXCEEDED);
    }

    @Test
    void should_rejectWithDailyLimitExceeded_when_450MillionUsedTodayAnd60MillionMore() {
        // given
        when(store.findUsed(SOURCE, BUSINESS_DAY)).thenReturn(Optional.of(new BigDecimal("450000000.00")));

        // when
        var thrown = catchThrowable(() -> service.checkCanCreate(request("60000000")));

        // then
        assertErrorCode(thrown, TransferErrorCode.DAILY_LIMIT_EXCEEDED);
    }

    @Test
    void should_recomputeFromDbInVietnamDay_when_redisHasNoUsage() {
        // given
        when(store.findUsed(SOURCE, BUSINESS_DAY)).thenReturn(Optional.empty());
        when(transferRepository.sumAmountReservedBetween(eq(SOURCE), anyCollection(), any(), any()))
                .thenReturn(new BigDecimal("450000000.00"));

        // when
        var thrown = catchThrowable(() -> service.checkCanCreate(request("60000000")));

        // then: ngay 24/11 gio Viet Nam = [23/11 17:00Z, 24/11 17:00Z), chi tinh lenh dang giu han muc
        assertErrorCode(thrown, TransferErrorCode.DAILY_LIMIT_EXCEEDED);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<TransferStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(transferRepository)
                .sumAmountReservedBetween(
                        eq(SOURCE),
                        statuses.capture(),
                        eq(Instant.parse("2026-11-23T17:00:00Z")),
                        eq(Instant.parse("2026-11-24T17:00:00Z")));
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(
                        TransferStatus.PROCESSING,
                        TransferStatus.PENDING_NAPAS,
                        TransferStatus.UNKNOWN,
                        TransferStatus.SUCCESS);
    }

    @Test
    void should_accept_when_withinBothLimits() {
        // given
        when(store.findUsed(SOURCE, BUSINESS_DAY)).thenReturn(Optional.of(new BigDecimal("400000000.00")));

        // when / then
        assertThatCode(() -> service.checkCanCreate(request("100000000"))).doesNotThrowAnyException();
    }

    @Test
    void should_returnReservation_when_reservedWithinDailyLimit() {
        // given
        var transfer = transfer("100000000");
        when(store.reserve(SOURCE, BUSINESS_DAY, transfer.getAmount(), DAILY_LIMIT))
                .thenReturn(LimitReservationOutcome.RESERVED);

        // when
        var reservation = service.tryReserve(transfer);

        // then
        assertThat(reservation).contains(new DailyLimitReservation(SOURCE, BUSINESS_DAY, transfer.getAmount()));
        verify(transferRepository, never()).sumAmountReservedBetween(anyString(), anyCollection(), any(), any());
    }

    @Test
    void should_returnEmpty_when_reservationExceedsDailyLimit() {
        // given
        var transfer = transfer("60000000");
        when(store.reserve(SOURCE, BUSINESS_DAY, transfer.getAmount(), DAILY_LIMIT))
                .thenReturn(LimitReservationOutcome.LIMIT_EXCEEDED);

        // when
        var reservation = service.tryReserve(transfer);

        // then
        assertThat(reservation).isEmpty();
    }

    @Test
    void should_initializeFromDbAndReserve_when_redisLostDailyUsage() {
        // given
        var transfer = transfer("60000000");
        var usedInDb = new BigDecimal("450000000.00");
        when(store.reserve(SOURCE, BUSINESS_DAY, transfer.getAmount(), DAILY_LIMIT))
                .thenReturn(LimitReservationOutcome.NOT_INITIALIZED);
        when(transferRepository.sumAmountReservedBetween(eq(SOURCE), anyCollection(), any(), any()))
                .thenReturn(usedInDb);
        when(store.initializeAndReserve(SOURCE, BUSINESS_DAY, usedInDb, transfer.getAmount(), DAILY_LIMIT))
                .thenReturn(LimitReservationOutcome.LIMIT_EXCEEDED);

        // when
        var reservation = service.tryReserve(transfer);

        // then
        assertThat(reservation).isEmpty();
        verify(store).initializeAndReserve(SOURCE, BUSINESS_DAY, usedInDb, transfer.getAmount(), DAILY_LIMIT);
    }

    @Test
    void should_releaseLimitOfReservationDay_when_failedTransferReleased() {
        // given: giu cho luc 23/11 16:00Z = 23:00 ngay 23/11 gio Viet Nam, that bai sau 00:00
        var transfer = transfer("100000000");
        transfer.changeStatus(TransferStatus.PROCESSING, CLOCK);
        transfer.fail("INSUFFICIENT_BALANCE", CLOCK);
        when(transferRepository.findLimitReservedAt(transfer.getTransferId()))
                .thenReturn(Optional.of(Instant.parse("2026-11-23T16:00:00Z")));

        // when
        service.release(transfer);

        // then
        verify(store).release(SOURCE, LocalDate.of(2026, 11, 23), transfer.getAmount());
    }

    @Test
    void should_notRelease_when_transferFailedBeforeReservingLimit() {
        // given: sai OTP qua so lan, chua tung giu cho
        var transfer = transfer("100000000");
        transfer.fail(TransferErrorCode.OTP_MAX_ATTEMPTS.code(), CLOCK);
        when(transferRepository.findLimitReservedAt(transfer.getTransferId())).thenReturn(Optional.empty());

        // when
        service.release(transfer);

        // then
        verify(store, never()).release(anyString(), any(LocalDate.class), any(BigDecimal.class));
    }

    @Test
    void should_refuseRelease_when_transferStillHoldsLimit() {
        // given
        var transfer = transfer("100000000");
        transfer.changeStatus(TransferStatus.PROCESSING, CLOCK);

        // when
        var thrown = catchThrowable(() -> service.release(transfer));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(store, never()).release(anyString(), any(LocalDate.class), any(BigDecimal.class));
    }

    @Test
    void should_releaseReservedAmount_when_reservationCancelled() {
        // given
        var reservation = new DailyLimitReservation(SOURCE, BUSINESS_DAY, new BigDecimal("100000000.00"));

        // when
        service.cancel(reservation);

        // then
        verify(store).release(SOURCE, BUSINESS_DAY, new BigDecimal("100000000.00"));
    }

    private static NewTransfer request(String amount) {
        return new NewTransfer(
                "C000000001",
                "0b9f3c1e-5a7d-4e2f-8c6b-1d2e3f4a5b6c",
                SOURCE,
                "100000000002",
                null,
                new BigDecimal(amount),
                BigDecimal.ZERO,
                "Chuyen tien");
    }

    private static Transfer transfer(String amount) {
        var transfer = Transfer.initiate("7f1c2a9e-1d2b-4c3d-9e8f-0a1b2c3d4e5f", request(amount), CLOCK);
        transfer.changeStatus(TransferStatus.OTP_PENDING, CLOCK);
        return transfer;
    }

    private static void assertErrorCode(Throwable thrown, ErrorCode code) {
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }
}
