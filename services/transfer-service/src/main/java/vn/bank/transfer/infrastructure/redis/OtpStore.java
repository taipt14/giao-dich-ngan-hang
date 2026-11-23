package vn.bank.transfer.infrastructure.redis;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import vn.bank.common.error.TechnicalException;
import vn.bank.transfer.config.OtpProperties;
import vn.bank.transfer.domain.TransferErrorCode;

/**
 * Hash OTP cua lenh va cac bo dem chong do OTP trong Redis. Moi thao tac kiem tra roi ghi la mot Lua script de atomic
 * (muc 12 convention): hai request xac thuc dung OTP cung luc chi mot request thanh cong. Day la luong bat buoc: Redis
 * loi thi nem {@code OTP_STORE_UNAVAILABLE} (503).
 */
@Component
@RequiredArgsConstructor
public class OtpStore {

    // KEYS: otp, cooldown. ARGV: hash, otp ttl ms, resend interval ms
    private static final RedisScript<Long> ISSUE = RedisScript.of("""
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
            return 1
            """, Long.class);

    // KEYS: otp, fail. ARGV: hash, max attempts, fail ttl ms. Tra 0 dung, -1 het han, -2 da het luot, n > 0 so lan sai
    private static final RedisScript<Long> VERIFY = RedisScript.of("""
            local fails = tonumber(redis.call('GET', KEYS[2]) or '0')
            if fails >= tonumber(ARGV[2]) then return -2 end
            local stored = redis.call('GET', KEYS[1])
            if not stored then return -1 end
            if stored == ARGV[1] then
              redis.call('DEL', KEYS[1], KEYS[2])
              return 0
            end
            fails = redis.call('INCR', KEYS[2])
            redis.call('PEXPIRE', KEYS[2], ARGV[3])
            return fails
            """, Long.class);

    // KEYS: otp, cooldown, resend, fail. ARGV: hash, otp ttl ms, interval ms, max resend, resend counter ttl ms.
    // Tra -2 het luot gui lai, -1 chua du khoang cach, n > 0 so lan da gui lai. Bo dem sai giu nguyen, gia han theo OTP
    // moi
    private static final RedisScript<Long> RESEND = RedisScript.of("""
            local count = tonumber(redis.call('GET', KEYS[3]) or '0')
            if count >= tonumber(ARGV[4]) then return -2 end
            if redis.call('EXISTS', KEYS[2]) == 1 then return -1 end
            redis.call('SET', KEYS[3], count + 1, 'PX', ARGV[5])
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
            redis.call('PEXPIRE', KEYS[4], ARGV[2])
            return count + 1
            """, Long.class);

    private static final long VERIFIED = 0;
    private static final long EXPIRED = -1;
    private static final long TOO_SOON = -1;
    private static final long NO_ATTEMPT_LEFT = -2;

    private final StringRedisTemplate redisTemplate;
    private final OtpProperties properties;

    /** Luu hash OTP dau tien cua lenh va bat dau khoang cho truoc khi duoc gui lai. */
    public void issue(String transferId, String otpHash) {
        execute(
                ISSUE,
                List.of(RedisKeys.otp(transferId), RedisKeys.otpCooldown(transferId)),
                otpHash,
                millis(properties.ttl()),
                millis(properties.resendInterval()));
    }

    /** So hash OTP khach hang nhap voi hash dang luu; dung thi xoa OTP (dung mot lan), sai thi tang bo dem sai. */
    public OtpVerification verify(String transferId, String otpHash) {
        long result = execute(
                VERIFY,
                List.of(RedisKeys.otp(transferId), RedisKeys.otpFail(transferId)),
                otpHash,
                String.valueOf(properties.maxAttempts()),
                millis(properties.ttl()));
        if (result == VERIFIED) {
            return new OtpVerification(OtpVerification.Outcome.VERIFIED, 0);
        }
        if (result == EXPIRED) {
            return new OtpVerification(OtpVerification.Outcome.EXPIRED, 0);
        }
        if (result == NO_ATTEMPT_LEFT || result >= properties.maxAttempts()) {
            return new OtpVerification(OtpVerification.Outcome.MAX_ATTEMPTS_REACHED, properties.maxAttempts());
        }
        return new OtpVerification(OtpVerification.Outcome.MISMATCH, Math.toIntExact(result));
    }

    /** Thay OTP cu bang OTP moi neu con luot gui lai va da qua khoang cach toi thieu tu lan gui truoc. */
    public OtpResendOutcome resend(String transferId, String otpHash) {
        long result = execute(
                RESEND,
                List.of(
                        RedisKeys.otp(transferId),
                        RedisKeys.otpCooldown(transferId),
                        RedisKeys.otpResend(transferId),
                        RedisKeys.otpFail(transferId)),
                otpHash,
                millis(properties.ttl()),
                millis(properties.resendInterval()),
                String.valueOf(properties.maxResend()),
                millis(properties.resendCounterTtl()));
        if (result == NO_ATTEMPT_LEFT) {
            return OtpResendOutcome.LIMIT_EXCEEDED;
        }
        return result == TOO_SOON ? OtpResendOutcome.TOO_SOON : OtpResendOutcome.RESENT;
    }

    /** Xoa moi key OTP cua lenh: lenh khong con cho OTP (da FAILED) hoac khong duoc tao. */
    public void discard(String transferId) {
        try {
            redisTemplate.delete(List.of(
                    RedisKeys.otp(transferId),
                    RedisKeys.otpFail(transferId),
                    RedisKeys.otpResend(transferId),
                    RedisKeys.otpCooldown(transferId)));
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    private long execute(RedisScript<Long> script, List<String> keys, String... args) {
        try {
            var result = redisTemplate.execute(script, keys, (Object[]) args);
            if (result == null) {
                throw new IllegalStateException("OTP script returned no result");
            }
            return result;
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    private static TechnicalException unavailable(DataAccessException e) {
        return new TechnicalException(TransferErrorCode.OTP_STORE_UNAVAILABLE, "Cannot access OTP store", e);
    }

    private static String millis(Duration duration) {
        return String.valueOf(duration.toMillis());
    }
}
