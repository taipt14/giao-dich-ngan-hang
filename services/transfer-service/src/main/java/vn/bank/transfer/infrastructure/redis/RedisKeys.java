package vn.bank.transfer.infrastructure.redis;

/** Key Redis cua transfer-service (muc 3.3, 12 convention; muc 6 kien-truc.md). */
public final class RedisKeys {

    private static final String IDEMPOTENCY = "idem:%s:%s";

    private RedisKeys() {}

    /**
     * Idempotency-Key cua request tao lenh; clientId la CIF cua khach hang (cung khoa voi UK_TRANSFER_CIF_IDEM_KEY).
     * TTL theo {@code bank.transfer.idempotency.ttl}.
     */
    public static String idempotency(String cif, String idempotencyKey) {
        return IDEMPOTENCY.formatted(cif, idempotencyKey);
    }
}
