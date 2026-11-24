package vn.bank.transfer.infrastructure.redis;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import vn.bank.common.error.TechnicalException;
import vn.bank.transfer.config.IdempotencyProperties;
import vn.bank.transfer.domain.TransferErrorCode;

/**
 * Lop idempotency thu nhat: {@code idem:{cif}:{idempotencyKey}} giu dau van tay noi dung request dau tien. Day la luong
 * bat buoc (muc 12 convention): Redis loi khi giu cho thi nem {@code IDEMPOTENCY_STORE_UNAVAILABLE} (503), khong xu ly
 * tiep ma khong co lop chong trung nay.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyStore {

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyProperties properties;

    /** {@code SET NX} kem TTL: true neu request nay giu duoc key, false neu key da co (request khac giu truoc). */
    public boolean tryAcquire(String cif, String idempotencyKey, String fingerprint) {
        try {
            var acquired = redisTemplate
                    .opsForValue()
                    .setIfAbsent(RedisKeys.idempotency(cif, idempotencyKey), fingerprint, properties.ttl());
            return Boolean.TRUE.equals(acquired);
        } catch (DataAccessException e) {
            throw new TechnicalException(
                    TransferErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE, "Cannot acquire idempotency key", e);
        }
    }

    /** Dau van tay da luu; rong neu key vua het han hoac bi xoa sau khi {@link #tryAcquire} that bai. */
    public Optional<String> findFingerprint(String cif, String idempotencyKey) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(RedisKeys.idempotency(cif, idempotencyKey)));
        } catch (DataAccessException e) {
            throw new TechnicalException(
                    TransferErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE, "Cannot read idempotency key", e);
        }
    }

    /**
     * Tra key khi khong tao duoc lenh, de client gui lai cung key (sau khi sua loi) khong bi bao dang xu ly. Xoa that
     * bai thi key con den het TTL, nen log WARN de theo doi.
     */
    public void release(String cif, String idempotencyKey) {
        try {
            redisTemplate.delete(RedisKeys.idempotency(cif, idempotencyKey));
        } catch (DataAccessException e) {
            log.warn("Cannot release idempotency key of customer {}", cif, e);
        }
    }
}
