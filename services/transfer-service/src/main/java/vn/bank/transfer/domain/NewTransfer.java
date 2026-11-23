package vn.bank.transfer.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Thong tin de tao lenh chuyen khoan. {@code destBankCode} null → lenh noi bo, khac null → lien ngan hang.
 * {@code description} co the null.
 */
public record NewTransfer(
        String cif,
        String idempotencyKey,
        String sourceAccount,
        String destAccount,
        String destBankCode,
        BigDecimal amount,
        BigDecimal fee,
        String description) {

    private static final int MONEY_SCALE = 2;
    private static final String FINGERPRINT_ALGORITHM = "SHA-256";
    // Gia tri null phan biet duoc voi chuoi rong va moi chuoi khac (do dai luon >= 0)
    private static final int NULL_LENGTH = -1;

    public TransferType type() {
        return TransferType.of(destBankCode);
    }

    /** Lenh noi bo tu mot tai khoan sang chinh no. Lien ngan hang cung so tai khoan la tai khoan cua ngan hang khac. */
    public boolean isSameAccountTransfer() {
        return type() == TransferType.INTERNAL && sourceAccount.equals(destAccount);
    }

    /**
     * Dau van tay noi dung lenh do khach hang gui (khong gom phi, do he thong tinh) de so sanh hai request cung
     * Idempotency-Key ma khong phai luu nguyen request. So tien so theo gia tri: {@code 100} va {@code 100.00} cung dau.
     */
    public String fingerprint() {
        var canonical = new StringBuilder();
        append(canonical, sourceAccount);
        append(canonical, destAccount);
        append(canonical, destBankCode);
        append(canonical, normalizedAmount().toPlainString());
        append(canonical, description);
        return HexFormat.of().formatHex(sha256(canonical.toString()));
    }

    /** Cung noi dung khach hang gui voi {@code transfer} (tai khoan, ngan hang dich, so tien, noi dung). */
    public boolean hasSameContentAs(Transfer transfer) {
        return sourceAccount.equals(transfer.getSourceAccount())
                && destAccount.equals(transfer.getDestAccount())
                && Objects.equals(destBankCode, transfer.getDestBankCode())
                && amount.compareTo(transfer.getAmount()) == 0
                && Objects.equals(description, transfer.getDescription());
    }

    private BigDecimal normalizedAmount() {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    // Moi truong ghi "do dai:gia tri" de "ab"+"c" va "a"+"bc" khong cho cung dau van tay
    private static void append(StringBuilder canonical, String value) {
        if (value == null) {
            canonical.append(NULL_LENGTH).append(':');
            return;
        }
        canonical.append(value.length()).append(':').append(value);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance(FINGERPRINT_ALGORITHM).digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // Moi JVM bat buoc ho tro SHA-256 (javadoc MessageDigest)
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
