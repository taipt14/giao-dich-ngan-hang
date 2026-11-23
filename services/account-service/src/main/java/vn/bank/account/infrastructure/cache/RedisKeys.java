package vn.bank.account.infrastructure.cache;

/** Key Redis cua account-service (muc 3.3, 12 convention; muc 6 kien-truc.md). */
public final class RedisKeys {

    private static final String BALANCE = "balance:%s";

    private RedisKeys() {}

    /** So du cua tai khoan (JSON), chi de hien thi. TTL theo {@code bank.account.balance-cache.ttl}. */
    public static String balance(String accountNo) {
        return BALANCE.formatted(accountNo);
    }
}
