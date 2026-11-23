package vn.bank.transfer.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hash cua OTP luu trong Redis thay cho OTP dang ro: SHA-256 cua {@code transferId:otp}. Ghep transferId lam salt de
 * cung mot ma OTP o hai lenh khac nhau cho hai hash khac nhau, va khong tra nguoc duoc bang bang hash 10^6 ma dung chung.
 */
public final class OtpHash {

    private static final String ALGORITHM = "SHA-256";
    private static final String SEPARATOR = ":";

    private OtpHash() {}

    public static String of(String transferId, String otp) {
        var value = transferId + SEPARATOR + otp;
        return HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance(ALGORITHM).digest(value);
        } catch (NoSuchAlgorithmException e) {
            // Moi JVM bat buoc ho tro SHA-256 (javadoc MessageDigest)
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
