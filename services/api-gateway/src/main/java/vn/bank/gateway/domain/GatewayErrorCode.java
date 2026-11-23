package vn.bank.gateway.domain;

import vn.bank.common.error.ErrorCode;

/** Ma loi rieng cua gateway. Loi framework (401, 403, 404, 503) dung ten HttpStatus, khong khai bao o day. */
public enum GatewayErrorCode implements ErrorCode {
    RATE_LIMIT_EXCEEDED(429);

    private final int httpStatus;

    GatewayErrorCode(int httpStatus) {
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
