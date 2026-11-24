package vn.bank.transfer.infrastructure.redis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import vn.bank.common.error.TechnicalException;
import vn.bank.transfer.domain.AccountNumberMasker;
import vn.bank.transfer.domain.BankZone;
import vn.bank.transfer.domain.TransferErrorCode;

/**
 * Tong tien da giu cho han muc trong ngay cua tai khoan nguon, key {@code limit:{accountNo}:{yyyyMMdd}} song den het
 * ngay nghiep vu ({@link BankZone}). Gia tri la so nguyen don vi 1/100 dong (scale 2) de dung {@code INCRBY}. Kiem tra
 * va cong la mot Lua script (atomic, muc 12 convention): nhieu lenh giu cho cung luc khong vuot han muc. Redis khong la
 * nguon goc: mat key thi noi goi tinh lai tu bang TRANSFER.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferLimitStore {

    private static final int MONEY_SCALE = 2;
    private static final String NO_BASELINE = "";

    // KEYS: limit. ARGV: amount, daily limit, used baseline ('' = khong co), expire at ms.
    // Tra -2 chua co key va khong co baseline, -1 vuot han muc, n > 0 tong da giu sau khi cong
    private static final RedisScript<Long> RESERVE = RedisScript.of("""
            local used = redis.call('GET', KEYS[1])
            if not used then
              if ARGV[3] == '' then return -2 end
              used = ARGV[3]
              redis.call('SET', KEYS[1], used, 'PXAT', ARGV[4])
            end
            if tonumber(used) + tonumber(ARGV[1]) > tonumber(ARGV[2]) then return -1 end
            return redis.call('INCRBY', KEYS[1], ARGV[1])
            """, Long.class);

    // KEYS: limit. ARGV: amount. Khong co key (het ngay, mat du lieu) thi khong lam gi; khong de tong am
    private static final RedisScript<Long> RELEASE = RedisScript.of("""
            local used = redis.call('GET', KEYS[1])
            if not used then return -1 end
            if tonumber(used) <= tonumber(ARGV[1]) then
              redis.call('SET', KEYS[1], '0', 'KEEPTTL')
              return 0
            end
            return redis.call('DECRBY', KEYS[1], ARGV[1])
            """, Long.class);

    private static final long LIMIT_EXCEEDED = -1;
    private static final long NOT_INITIALIZED = -2;

    private final StringRedisTemplate redisTemplate;

    /**
     * Tong da giu trong ngay {@code day}; rong neu chua co key. Day la luong doc (kiem tra luc tao lenh): Redis loi thi
     * log WARN va tra rong de noi goi tinh tu DB (muc 12 convention).
     */
    public Optional<BigDecimal> findUsed(String accountNo, LocalDate day) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(RedisKeys.dailyLimit(accountNo, day)))
                    .map(used -> BigDecimal.valueOf(Long.parseLong(used), MONEY_SCALE));
        } catch (DataAccessException e) {
            log.warn("Cannot read daily limit of account {}, fall back to DB", AccountNumberMasker.mask(accountNo), e);
            return Optional.empty();
        }
    }

    /** Giu cho {@code amount} neu tong sau khi cong khong vuot {@code dailyLimit}; chua co key → NOT_INITIALIZED. */
    public LimitReservationOutcome reserve(String accountNo, LocalDate day, BigDecimal amount, BigDecimal dailyLimit) {
        return execute(accountNo, day, amount, dailyLimit, NO_BASELINE);
    }

    /**
     * Nhu {@link #reserve}, nhung chua co key thi tao key voi tong {@code usedBaseline} (tinh tu DB) roi giu cho trong
     * cung script. Key da co (request khac vua tao) thi bo qua baseline, dung gia tri dang co.
     */
    public LimitReservationOutcome initializeAndReserve(
            String accountNo, LocalDate day, BigDecimal usedBaseline, BigDecimal amount, BigDecimal dailyLimit) {
        return execute(accountNo, day, amount, dailyLimit, String.valueOf(toUnits(usedBaseline)));
    }

    /**
     * Tra lai {@code amount} da giu trong ngay {@code day}. Redis loi thi log WARN, khong nem: phan da giu con lai den het
     * ngay (khach hang tam bi giam han muc, khong bao gio vuot han muc).
     */
    public void release(String accountNo, LocalDate day, BigDecimal amount) {
        try {
            redisTemplate.execute(
                    RELEASE, List.of(RedisKeys.dailyLimit(accountNo, day)), String.valueOf(toUnits(amount)));
        } catch (DataAccessException e) {
            log.warn("Cannot release daily limit of account {}", AccountNumberMasker.mask(accountNo), e);
        }
    }

    private LimitReservationOutcome execute(
            String accountNo, LocalDate day, BigDecimal amount, BigDecimal dailyLimit, String usedBaseline) {
        Long result;
        try {
            result = redisTemplate.execute(
                    RESERVE,
                    List.of(RedisKeys.dailyLimit(accountNo, day)),
                    String.valueOf(toUnits(amount)),
                    String.valueOf(toUnits(dailyLimit)),
                    usedBaseline,
                    String.valueOf(endOf(day)));
        } catch (DataAccessException e) {
            throw new TechnicalException(
                    TransferErrorCode.TRANSFER_LIMIT_STORE_UNAVAILABLE, "Cannot reserve daily transfer limit", e);
        }
        if (result == null) {
            throw new IllegalStateException("Daily limit script returned no result");
        }
        if (result == NOT_INITIALIZED) {
            return LimitReservationOutcome.NOT_INITIALIZED;
        }
        return result == LIMIT_EXCEEDED ? LimitReservationOutcome.LIMIT_EXCEEDED : LimitReservationOutcome.RESERVED;
    }

    // 00:00 ngay hom sau theo gio Viet Nam: han muc tinh lai tu dau ngay
    private static long endOf(LocalDate day) {
        return day.plusDays(1).atStartOfDay(BankZone.ZONE).toInstant().toEpochMilli();
    }

    private static long toUnits(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                .unscaledValue()
                .longValueExact();
    }
}
