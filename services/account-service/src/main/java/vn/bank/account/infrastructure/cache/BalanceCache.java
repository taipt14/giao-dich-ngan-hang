package vn.bank.account.infrastructure.cache;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.json.JsonMapper;
import vn.bank.account.config.BalanceCacheProperties;
import vn.bank.account.domain.AccountBalance;
import vn.bank.account.domain.AccountNumberMasker;

/**
 * Cache so du {@code balance:{accountNo}} dang JSON. Redis khong phai nguon du lieu goc (muc 12 convention): moi loi
 * Redis chi log WARN, doc coi nhu khong co cache, ghi/xoa bo qua, de API van doc duoc tu DB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BalanceCache {

    // Tach rieng voi JsonMapper cua REST: doi cau hinh web khong lam hong gia tri dang nam trong cache
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private final StringRedisTemplate redisTemplate;
    private final BalanceCacheProperties properties;

    public Optional<AccountBalance> find(String accountNo) {
        try {
            var json = redisTemplate.opsForValue().get(RedisKeys.balance(accountNo));
            return Optional.ofNullable(json).map(value -> MAPPER.readValue(value, AccountBalance.class));
        } catch (DataAccessException e) {
            log.warn("Cannot read balance cache of {}, falling back to DB", AccountNumberMasker.mask(accountNo), e);
            return Optional.empty();
        } catch (JacksonException e) {
            log.warn("Invalid balance cache of {}, falling back to DB", AccountNumberMasker.mask(accountNo), e);
            return Optional.empty();
        }
    }

    public void put(AccountBalance balance) {
        try {
            redisTemplate
                    .opsForValue()
                    .set(RedisKeys.balance(balance.accountNo()), MAPPER.writeValueAsString(balance), properties.ttl());
        } catch (DataAccessException e) {
            log.warn("Cannot write balance cache of {}", AccountNumberMasker.mask(balance.accountNo()), e);
        }
    }

    /** Xoa that bai thi cache cu con toi da het TTL, nen log WARN de theo doi. */
    public void evict(String accountNo) {
        try {
            redisTemplate.delete(RedisKeys.balance(accountNo));
        } catch (DataAccessException e) {
            log.warn("Cannot evict balance cache of {}", AccountNumberMasker.mask(accountNo), e);
        }
    }
}
