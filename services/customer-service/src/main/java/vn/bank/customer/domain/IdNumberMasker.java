package vn.bank.customer.domain;

/**
 * Che so CCCD truoc khi dua ra log hoac response (muc 13 convention): giu 4 so dau va 2 so cuoi, vi du
 * {@code 001234567889} thanh {@code 0012******89}.
 */
public final class IdNumberMasker {

    private static final int VISIBLE_PREFIX = 4;
    private static final int VISIBLE_SUFFIX = 2;
    private static final String MASK_CHAR = "*";

    private IdNumberMasker() {}

    public static String mask(String idNumber) {
        if (idNumber.length() <= VISIBLE_PREFIX + VISIBLE_SUFFIX) {
            return MASK_CHAR.repeat(idNumber.length());
        }
        var hiddenLength = idNumber.length() - VISIBLE_PREFIX - VISIBLE_SUFFIX;
        return idNumber.substring(0, VISIBLE_PREFIX)
                + MASK_CHAR.repeat(hiddenLength)
                + idNumber.substring(idNumber.length() - VISIBLE_SUFFIX);
    }
}
