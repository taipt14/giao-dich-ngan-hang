package vn.bank.common.error;

/** Ma loi dung chung cho moi service, do GlobalExceptionHandler tra ve. */
public enum CommonErrorCode implements ErrorCode {
    VALIDATION_FAILED(400),
    INTERNAL_ERROR(500);

    private final int httpStatus;

    CommonErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
