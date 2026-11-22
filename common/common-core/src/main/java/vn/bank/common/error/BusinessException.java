package vn.bank.common.error;

import java.util.Objects;

/** Loi vi pham quy tac nghiep vu; message duoc tra ve cho client. */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
