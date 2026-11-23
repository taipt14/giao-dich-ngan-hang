package vn.bank.transfer.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import vn.bank.common.test.AbstractIntegrationTest;
import vn.bank.transfer.config.OtpProperties;
import vn.bank.transfer.domain.OtpHash;
import vn.bank.transfer.infrastructure.redis.OtpVerification.Outcome;

/**
 * Lua script cua {@link OtpStore} tren Redis that. Khoang cach gui lai rut ngan de test khong phai cho 30 giay; TTL OTP
 * giu dung 3 phut de kiem tra TTL thuc te cua key.
 */
class OtpStoreIT extends AbstractIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final Duration OTP_TTL = Duration.ofMinutes(3);
    private static final Duration RESEND_INTERVAL = Duration.ofMillis(300);
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_RESEND = 3;
    private static final String OTP = "042917";
    private static final String WRONG_OTP = "999999";

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private OtpStore store;
    private String transferId;

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
        var properties =
                new OtpProperties(OTP_TTL, MAX_ATTEMPTS, MAX_RESEND, RESEND_INTERVAL, Duration.ofHours(24), false);
        store = new OtpStore(redisTemplate, properties);
        transferId = UUID.randomUUID().toString();
    }

    @Test
    void should_storeOnlyOtpHashWithThreeMinuteTtl_when_issued() {
        // when
        store.issue(transferId, hash(OTP));

        // then
        var key = "otp:" + transferId;
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(hash(OTP)).doesNotContain(OTP);
        assertThat(Duration.ofMillis(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)))
                .isBetween(OTP_TTL.minusSeconds(5), OTP_TTL);
    }

    @Test
    void should_verifyOnceThenReportExpired_when_correctOtpReused() {
        // given
        store.issue(transferId, hash(OTP));

        // when
        var first = store.verify(transferId, hash(OTP));
        var second = store.verify(transferId, hash(OTP));

        // then: OTP dung mot lan
        assertThat(first.outcome()).isEqualTo(Outcome.VERIFIED);
        assertThat(second.outcome()).isEqualTo(Outcome.EXPIRED);
    }

    @Test
    void should_reportExpired_when_otpTtlElapsed() {
        // given
        store.issue(transferId, hash(OTP));
        redisTemplate.expire("otp:" + transferId, Duration.ofMillis(1));
        await().atMost(Duration.ofSeconds(2)).until(() -> !redisTemplate.hasKey("otp:" + transferId));

        // when
        var verification = store.verify(transferId, hash(OTP));

        // then
        assertThat(verification.outcome()).isEqualTo(Outcome.EXPIRED);
    }

    @Test
    void should_countWrongAttemptsAndReachMaxOnFifth_when_wrongOtpRepeated() {
        // given
        store.issue(transferId, hash(OTP));

        // when
        var outcomes = new ArrayList<OtpVerification>();
        for (var i = 0; i < MAX_ATTEMPTS; i++) {
            outcomes.add(store.verify(transferId, hash(WRONG_OTP)));
        }
        var correctAfterMax = store.verify(transferId, hash(OTP));

        // then
        assertThat(outcomes.subList(0, MAX_ATTEMPTS - 1))
                .extracting(OtpVerification::outcome, OtpVerification::failedAttempts)
                .containsExactly(
                        tuple(Outcome.MISMATCH, 1),
                        tuple(Outcome.MISMATCH, 2),
                        tuple(Outcome.MISMATCH, 3),
                        tuple(Outcome.MISMATCH, 4));
        assertThat(outcomes.get(MAX_ATTEMPTS - 1).outcome()).isEqualTo(Outcome.MAX_ATTEMPTS_REACHED);
        assertThat(correctAfterMax.outcome()).isEqualTo(Outcome.MAX_ATTEMPTS_REACHED);
        var failTtl = redisTemplate.getExpire("otp:fail:" + transferId);
        assertThat(failTtl).isPositive().isLessThanOrEqualTo(OTP_TTL.toSeconds());
    }

    @Test
    void should_rejectResend_when_withinIntervalOfPreviousSend() {
        // given
        store.issue(transferId, hash(OTP));

        // when
        var outcome = store.resend(transferId, hash(WRONG_OTP));

        // then: OTP cu van con hieu luc
        assertThat(outcome).isEqualTo(OtpResendOutcome.TOO_SOON);
        assertThat(store.verify(transferId, hash(OTP)).outcome()).isEqualTo(Outcome.VERIFIED);
    }

    @Test
    void should_replaceOtpUpToThreeTimes_when_resentAfterInterval() {
        // given
        store.issue(transferId, hash(OTP));
        var outcomes = new ArrayList<OtpResendOutcome>();

        // when
        for (var i = 0; i <= MAX_RESEND; i++) {
            awaitCooldownOver();
            outcomes.add(store.resend(transferId, hash("10000" + i)));
        }

        // then: OTP moi thay OTP cu
        assertThat(outcomes)
                .containsExactly(
                        OtpResendOutcome.RESENT,
                        OtpResendOutcome.RESENT,
                        OtpResendOutcome.RESENT,
                        OtpResendOutcome.LIMIT_EXCEEDED);
        assertThat(store.verify(transferId, hash(OTP)).outcome()).isEqualTo(Outcome.MISMATCH);
        assertThat(store.verify(transferId, hash("100002")).outcome()).isEqualTo(Outcome.VERIFIED);
    }

    @Test
    void should_keepWrongAttemptCount_when_otpResent() {
        // given
        store.issue(transferId, hash(OTP));
        store.verify(transferId, hash(WRONG_OTP));
        store.verify(transferId, hash(WRONG_OTP));
        awaitCooldownOver();

        // when
        store.resend(transferId, hash("100000"));
        var verification = store.verify(transferId, hash(WRONG_OTP));

        // then: gui lai khong cho them luot nhap sai
        assertThat(verification.failedAttempts()).isEqualTo(3);
    }

    @Test
    void should_removeAllOtpKeys_when_discarded() {
        // given
        store.issue(transferId, hash(OTP));
        store.verify(transferId, hash(WRONG_OTP));

        // when
        store.discard(transferId);

        // then
        assertThat(redisTemplate.keys("otp:*" + transferId)).isEmpty();
    }

    private void awaitCooldownOver() {
        await().atMost(Duration.ofSeconds(2)).until(() -> !redisTemplate.hasKey("otp:cooldown:" + transferId));
    }

    private String hash(String otp) {
        return OtpHash.of(transferId, otp);
    }
}
