package vn.bank.account.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import vn.bank.account.config.BalanceCacheProperties;
import vn.bank.account.domain.AccountBalance;

class BalanceCacheTest {

    private static final String ACCOUNT_NO = "100000000001";
    private static final String KEY = "balance:" + ACCOUNT_NO;
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final AccountBalance BALANCE =
            new AccountBalance(ACCOUNT_NO, "C000000001", "VND", new BigDecimal("1000000.00"), new BigDecimal("0.00"));

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private final BalanceCache balanceCache = new BalanceCache(redisTemplate, new BalanceCacheProperties(TTL));

    @BeforeEach
    void stubValueOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void should_writeBalanceWithTtlAndReadItBack_when_cached() {
        // given
        var json = ArgumentCaptor.forClass(String.class);

        // when
        balanceCache.put(BALANCE);
        verify(valueOperations).set(eq(KEY), json.capture(), eq(TTL));
        when(valueOperations.get(KEY)).thenReturn(json.getValue());
        var cached = balanceCache.find(ACCOUNT_NO);

        // then
        assertThat(json.getValue()).contains("1000000.00");
        assertThat(cached).contains(BALANCE);
    }

    @Test
    void should_returnEmpty_when_redisUnavailableOnRead() {
        // given
        when(valueOperations.get(KEY)).thenThrow(new RedisConnectionFailureException("Redis down"));

        // when
        var cached = balanceCache.find(ACCOUNT_NO);

        // then
        assertThat(cached).isEmpty();
    }

    @Test
    void should_returnEmpty_when_cachedValueCorrupted() {
        // given
        when(valueOperations.get(KEY)).thenReturn("not-json");

        // when
        var cached = balanceCache.find(ACCOUNT_NO);

        // then
        assertThat(cached).isEmpty();
    }

    @Test
    void should_notThrow_when_redisUnavailableOnWriteOrEvict() {
        // given
        doThrow(new RedisConnectionFailureException("Redis down"))
                .when(valueOperations)
                .set(anyString(), anyString(), any(Duration.class));
        when(redisTemplate.delete(KEY)).thenThrow(new RedisConnectionFailureException("Redis down"));

        // when / then
        assertThatCode(() -> balanceCache.put(BALANCE)).doesNotThrowAnyException();
        assertThatCode(() -> balanceCache.evict(ACCOUNT_NO)).doesNotThrowAnyException();
    }
}
