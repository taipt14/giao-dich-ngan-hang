package vn.bank.transfer.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import vn.bank.common.test.AbstractIntegrationTest;
import vn.bank.transfer.domain.BankZone;

/** Lua script cua {@link TransferLimitStore} tren Redis that: giu cho atomic, key song den 00:00 hom sau gio Viet Nam. */
class TransferLimitStoreIT extends AbstractIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final int CONCURRENT_TRANSFERS = 10;
    private static final BigDecimal DAILY_LIMIT = new BigDecimal("500000000.00");
    private static final BigDecimal HUNDRED_MILLION = new BigDecimal("100000000");

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    // Hom nay theo gio Viet Nam: key het han that vao 00:00 toi nay, khong de rac lau trong Redis dung lai
    private final LocalDate day = LocalDate.now(Clock.system(BankZone.ZONE));
    private TransferLimitStore store;
    private String accountNo;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        store = new TransferLimitStore(redisTemplate);
        accountNo = "1000%08d".formatted(ThreadLocalRandom.current().nextLong(1, 100_000_000L));
    }

    @Test
    void should_allowAtMostFiveReservations_when_tenConcurrentTransfersOf100Million() throws Exception {
        // given: key da co (dau ngay chua chuyen gi)
        store.initializeAndReserve(accountNo, day, BigDecimal.ZERO, BigDecimal.ONE, DAILY_LIMIT);
        store.release(accountNo, day, BigDecimal.ONE);
        var start = new CountDownLatch(1);
        var tasks = new ArrayList<Callable<LimitReservationOutcome>>();
        for (var i = 0; i < CONCURRENT_TRANSFERS; i++) {
            tasks.add(() -> {
                start.await();
                return store.reserve(accountNo, day, HUNDRED_MILLION, DAILY_LIMIT);
            });
        }

        // when
        var outcomes = runConcurrently(tasks, start);

        // then
        assertThat(outcomes)
                .filteredOn(LimitReservationOutcome.RESERVED::equals)
                .hasSize(5);
        assertThat(outcomes)
                .filteredOn(LimitReservationOutcome.LIMIT_EXCEEDED::equals)
                .hasSize(5);
        assertThat(usedUnits()).isEqualTo("50000000000");
    }

    @Test
    void should_allowAtMostFiveReservations_when_tenConcurrentTransfersInitializeLostKey() throws Exception {
        // given: Redis mat key, moi request tu tinh baseline tu DB (0) roi giu cho
        var start = new CountDownLatch(1);
        var tasks = new ArrayList<Callable<LimitReservationOutcome>>();
        for (var i = 0; i < CONCURRENT_TRANSFERS; i++) {
            tasks.add(() -> {
                start.await();
                return store.initializeAndReserve(accountNo, day, BigDecimal.ZERO, HUNDRED_MILLION, DAILY_LIMIT);
            });
        }

        // when
        var outcomes = runConcurrently(tasks, start);

        // then: baseline chi duoc dung mot lan, cac request sau cong vao gia tri dang co
        assertThat(outcomes)
                .filteredOn(LimitReservationOutcome.RESERVED::equals)
                .hasSize(5);
        assertThat(usedUnits()).isEqualTo("50000000000");
    }

    @Test
    void should_reportNotInitialized_when_keyMissingAndNoBaseline() {
        // when
        var outcome = store.reserve(accountNo, day, HUNDRED_MILLION, DAILY_LIMIT);

        // then
        assertThat(outcome).isEqualTo(LimitReservationOutcome.NOT_INITIALIZED);
        assertThat(redisTemplate.hasKey(key())).isFalse();
    }

    @Test
    void should_expireKeyAtNextMidnightInVietnam_when_initialized() {
        // when
        store.initializeAndReserve(accountNo, day, new BigDecimal("450000000"), HUNDRED_MILLION, DAILY_LIMIT);

        // then: vuot han muc van giu baseline tinh tu DB
        var expireAt = Instant.now().plusMillis(redisTemplate.getExpire(key(), TimeUnit.MILLISECONDS));
        var nextMidnight = day.plusDays(1).atStartOfDay(BankZone.ZONE).toInstant();
        assertThat(expireAt).isBetween(nextMidnight.minusSeconds(5), nextMidnight.plusSeconds(5));
        assertThat(usedUnits()).isEqualTo("45000000000");
    }

    @Test
    void should_returnLimit_when_released() {
        // given
        store.initializeAndReserve(accountNo, day, new BigDecimal("400000000"), HUNDRED_MILLION, DAILY_LIMIT);

        // when
        store.release(accountNo, day, HUNDRED_MILLION);

        // then
        assertThat(store.findUsed(accountNo, day)).contains(new BigDecimal("400000000.00"));
        assertThat(store.reserve(accountNo, day, HUNDRED_MILLION, DAILY_LIMIT))
                .isEqualTo(LimitReservationOutcome.RESERVED);
    }

    @Test
    void should_neverGoBelowZeroNorCreateKey_when_releasedMoreThanUsedOrKeyMissing() {
        // given
        store.initializeAndReserve(accountNo, day, BigDecimal.ZERO, new BigDecimal("1000"), DAILY_LIMIT);
        var otherDay = day.minusDays(1);

        // when
        store.release(accountNo, day, HUNDRED_MILLION);
        store.release(accountNo, otherDay, HUNDRED_MILLION);

        // then
        assertThat(usedUnits()).isEqualTo("0");
        assertThat(redisTemplate.getExpire(key())).isPositive();
        assertThat(redisTemplate.hasKey(RedisKeys.dailyLimit(accountNo, otherDay)))
                .isFalse();
    }

    private static List<LimitReservationOutcome> runConcurrently(
            List<Callable<LimitReservationOutcome>> tasks, CountDownLatch start) throws Exception {
        try (var executor = Executors.newFixedThreadPool(tasks.size())) {
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            var outcomes = new ArrayList<LimitReservationOutcome>();
            for (Future<LimitReservationOutcome> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        }
    }

    private String usedUnits() {
        return redisTemplate.opsForValue().get(key());
    }

    private String key() {
        return RedisKeys.dailyLimit(accountNo, day);
    }
}
