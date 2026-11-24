package vn.bank.transfer.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import vn.bank.common.error.TechnicalException;
import vn.bank.transfer.config.IdempotencyProperties;
import vn.bank.transfer.domain.TransferErrorCode;

/** Redis loi o luong idempotency bat buoc → 503 (muc 12 convention), khong xu ly tiep khi khong chong trung duoc. */
class IdempotencyStoreTest {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String CIF = "C000000001";
    private static final String KEY = "0b9f3c1e-5a7d-4e2f-8c6b-1d2e3f4a5b6c";
    private static final String REDIS_KEY = "idem:C000000001:0b9f3c1e-5a7d-4e2f-8c6b-1d2e3f4a5b6c";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private final IdempotencyStore store = new IdempotencyStore(redisTemplate, new IdempotencyProperties(TTL));

    @BeforeEach
    void stubValueOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void should_setKeyIfAbsentWithTtl_when_acquiring() {
        // given
        when(valueOperations.setIfAbsent(REDIS_KEY, "fingerprint", TTL)).thenReturn(true);

        // when
        var acquired = store.tryAcquire(CIF, KEY, "fingerprint");

        // then
        assertThat(acquired).isTrue();
        verify(valueOperations).setIfAbsent(REDIS_KEY, "fingerprint", TTL);
    }

    @Test
    void should_throwIdempotencyStoreUnavailable_when_redisDownOnAcquire() {
        // given
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        // when
        var thrown = catchThrowable(() -> store.tryAcquire(CIF, KEY, "fingerprint"));

        // then
        assertThat(thrown)
                .isInstanceOf(TechnicalException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE);
    }

    @Test
    void should_throwIdempotencyStoreUnavailable_when_redisDownOnRead() {
        // given
        when(valueOperations.get(REDIS_KEY)).thenThrow(new RedisConnectionFailureException("down"));

        // when
        var thrown = catchThrowable(() -> store.findFingerprint(CIF, KEY));

        // then
        assertThat(thrown)
                .isInstanceOf(TechnicalException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.IDEMPOTENCY_STORE_UNAVAILABLE);
    }

    @Test
    void should_notThrow_when_redisDownOnRelease() {
        // given
        when(redisTemplate.delete(REDIS_KEY)).thenThrow(new RedisConnectionFailureException("down"));

        // when / then: key con den het TTL, request khong bi loi them
        assertThatCode(() -> store.release(CIF, KEY)).doesNotThrowAnyException();
    }
}
