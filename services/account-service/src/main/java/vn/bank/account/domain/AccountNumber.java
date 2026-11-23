package vn.bank.account.domain;

/** So tai khoan thanh toan: {@code 1000} + 8 chu so lay tu sequence {@code SEQ_ACCOUNT_NO}, tong 12 chu so. */
public final class AccountNumber {

    private static final String PREFIX = "1000";
    private static final String DIGITS_FORMAT = "%08d";
    private static final long MAX_SEQUENCE = 99_999_999L;

    private AccountNumber() {}

    public static String fromSequence(long sequenceValue) {
        if (sequenceValue < 1 || sequenceValue > MAX_SEQUENCE) {
            throw new IllegalArgumentException("Account number sequence out of range: " + sequenceValue);
        }
        return PREFIX + DIGITS_FORMAT.formatted(sequenceValue);
    }
}
