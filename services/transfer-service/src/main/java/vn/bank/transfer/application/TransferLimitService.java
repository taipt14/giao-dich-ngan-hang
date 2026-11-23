package vn.bank.transfer.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.bank.transfer.domain.AccountNumberMasker;
import vn.bank.transfer.domain.BankZone;
import vn.bank.transfer.domain.CustomerType;
import vn.bank.transfer.domain.NewTransfer;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferLimitConfig;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.infrastructure.persistence.TransferLimitConfigRepository;
import vn.bank.transfer.infrastructure.persistence.TransferRepository;
import vn.bank.transfer.infrastructure.redis.LimitReservationOutcome;
import vn.bank.transfer.infrastructure.redis.TransferLimitStore;

/**
 * Han muc chuyen khoan (BANK-043): moi lenh va tong moi ngay nghiep vu ({@link BankZone}) cua tai khoan nguon. Luc tao
 * lenh chi kiem tra; luc xac thuc OTP moi giu cho (Redis, atomic); lenh FAILED/REVERSED thi hoan lai. Redis mat key thi
 * tinh lai tu bang TRANSFER: cac lenh dang giu cho ({@link TransferStatus#holdsDailyLimit}) va da xac thuc OTP trong
 * ngay. Khong mo transaction DB: thao tac Redis nam ngoai transaction (muc 8 convention).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferLimitService {

    // customer-service chua co thong tin loai khach hang: moi CIF dung han muc mac dinh
    private static final CustomerType DEFAULT_CUSTOMER_TYPE = CustomerType.STANDARD;

    private static final Set<TransferStatus> HOLDING_STATUSES = Arrays.stream(TransferStatus.values())
            .filter(TransferStatus::holdsDailyLimit)
            .collect(Collectors.toUnmodifiableSet());

    private final TransferLimitConfigRepository transferLimitConfigRepository;
    private final TransferRepository transferRepository;
    private final TransferLimitStore transferLimitStore;
    private final Clock clock;

    /**
     * Kiem tra truoc khi tao lenh, chua giu cho: vuot han muc moi lenh → {@code PER_TRANSACTION_LIMIT_EXCEEDED}; tong da
     * giu trong ngay cong so tien vuot han muc ngay → {@code DAILY_LIMIT_EXCEEDED}.
     */
    public void checkCanCreate(NewTransfer request) {
        var config = getConfig();
        config.requireWithinPerTransactionLimit(request.amount());
        var today = today();
        var used = transferLimitStore
                .findUsed(request.sourceAccount(), today)
                .orElseGet(() -> reservedInDb(request.sourceAccount(), today));
        config.requireWithinDailyLimit(used, request.amount());
    }

    /**
     * Giu cho so tien cua lenh trong han muc ngay hom nay; rong neu vuot han muc (khong giu gi). Redis loi →
     * {@code TRANSFER_LIMIT_STORE_UNAVAILABLE} (503).
     */
    public Optional<DailyLimitReservation> tryReserve(Transfer transfer) {
        var dailyLimit = getConfig().getDailyLimit();
        var reservation = new DailyLimitReservation(transfer.getSourceAccount(), today(), transfer.getAmount());
        var outcome = transferLimitStore.reserve(
                reservation.sourceAccount(), reservation.day(), reservation.amount(), dailyLimit);
        if (outcome == LimitReservationOutcome.NOT_INITIALIZED) {
            var used = reservedInDb(reservation.sourceAccount(), reservation.day());
            outcome = transferLimitStore.initializeAndReserve(
                    reservation.sourceAccount(), reservation.day(), used, reservation.amount(), dailyLimit);
        }
        if (outcome == LimitReservationOutcome.RESERVED) {
            return Optional.of(reservation);
        }
        log.info(
                "Transfer {} exceeds daily limit of account {}",
                transfer.getTransferId(),
                AccountNumberMasker.mask(transfer.getSourceAccount()));
        return Optional.empty();
    }

    /** Tra lai phan vua giu khi khong chuyen duoc lenh sang PROCESSING. */
    public void cancel(DailyLimitReservation reservation) {
        transferLimitStore.release(reservation.sourceAccount(), reservation.day(), reservation.amount());
    }

    /**
     * Hoan han muc ngay da giu cho lenh {@code transfer} da FAILED/REVERSED, vao dung ngay da giu. Lenh that bai truoc
     * khi giu cho (vi du sai OTP qua so lan) thi khong lam gi.
     */
    public void release(Transfer transfer) {
        if (!transfer.getStatus().releasesDailyLimit()) {
            throw new IllegalStateException(
                    "Transfer " + transfer.getTransferId() + " is " + transfer.getStatus() + ", limit is still held");
        }
        var reservedAt = transferRepository.findLimitReservedAt(transfer.getTransferId());
        if (reservedAt.isEmpty()) {
            return;
        }
        var day = LocalDate.ofInstant(reservedAt.get(), BankZone.ZONE);
        transferLimitStore.release(transfer.getSourceAccount(), day, transfer.getAmount());
        log.info("Transfer {} {}, daily limit released", transfer.getTransferId(), transfer.getStatus());
    }

    private BigDecimal reservedInDb(String sourceAccount, LocalDate day) {
        var from = day.atStartOfDay(BankZone.ZONE).toInstant();
        var to = day.plusDays(1).atStartOfDay(BankZone.ZONE).toInstant();
        return transferRepository.sumAmountReservedBetween(sourceAccount, HOLDING_STATUSES, from, to);
    }

    private TransferLimitConfig getConfig() {
        return transferLimitConfigRepository
                .findById(DEFAULT_CUSTOMER_TYPE)
                .orElseThrow(
                        () -> new IllegalStateException("No transfer limit configured for " + DEFAULT_CUSTOMER_TYPE));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(BankZone.ZONE));
    }
}
