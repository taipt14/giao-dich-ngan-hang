package vn.bank.customer.domain;

/** Ma khach hang: {@code C} + 9 chu so lay tu sequence {@code SEQ_CIF}. */
public final class Cif {

    private static final String PREFIX = "C";
    private static final String DIGITS_FORMAT = "%09d";
    private static final long MAX_SEQUENCE = 999_999_999L;

    private Cif() {}

    public static String fromSequence(long sequenceValue) {
        if (sequenceValue < 1 || sequenceValue > MAX_SEQUENCE) {
            throw new IllegalArgumentException("CIF sequence out of range: " + sequenceValue);
        }
        return PREFIX + DIGITS_FORMAT.formatted(sequenceValue);
    }
}
