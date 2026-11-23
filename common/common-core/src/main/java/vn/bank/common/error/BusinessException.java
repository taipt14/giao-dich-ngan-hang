package vn.bank.common.error;

import java.util.Objects;

/** Loi vi pham quy tac nghiep vu; message duoc tra ve cho client. */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    /**
     * Dung khi loi nghiep vu sinh ra tu mot exception khac (vi du xung dot optimistic lock): giu {@code cause} goc de
     * truy vet (muc 9 convention).
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
