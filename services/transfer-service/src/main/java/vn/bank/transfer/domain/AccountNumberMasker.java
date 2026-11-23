package vn.bank.transfer.domain;

/**
 * Che so tai khoan truoc khi ghi log (muc 13 convention): giu 4 so dau va 4 so cuoi, vi du {@code 100000005678} thanh
 * {@code 1000****5678}.
 */
public final class AccountNumberMasker {

    private static final int VISIBLE_PREFIX = 4;
    private static final int VISIBLE_SUFFIX = 4;
    private static final int MASK_LENGTH = 4;
    private static final String MASK_CHAR = "*";

    private AccountNumberMasker() {}

    public static String mask(String accountNo) {
        if (accountNo.length() <= VISIBLE_PREFIX + VISIBLE_SUFFIX) {
            return MASK_CHAR.repeat(accountNo.length());
        }
        return accountNo.substring(0, VISIBLE_PREFIX)
                + MASK_CHAR.repeat(MASK_LENGTH)
                + accountNo.substring(accountNo.length() - VISIBLE_SUFFIX);
    }
}
