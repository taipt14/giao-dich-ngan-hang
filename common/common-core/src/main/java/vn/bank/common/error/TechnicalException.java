package vn.bank.common.error;

import java.util.Objects;

/** Loi ky thuat (timeout, phu thuoc khong san sang...); luon giu cause goc. */
public class TechnicalException extends RuntimeException {

    private final ErrorCode errorCode;

    public TechnicalException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
