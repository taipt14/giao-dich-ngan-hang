package vn.bank.gateway.infrastructure.ratelimit;

/** Key Redis cua gateway (muc 3.3, 12 convention). */
public final class RedisKeys {

    private static final String RATE_LIMIT = "ratelimit:%s:%s";

    private RedisKeys() {}

    /**
     * Sorted set cac request da duoc cho qua cua user tren mot route trong cua so rate limit (score = thoi diem, ms).
     * TTL = do dai cua so, gia han moi lan request.
     */
    public static String rateLimit(String routeId, String userId) {
        return RATE_LIMIT.formatted(routeId, userId);
    }
}
