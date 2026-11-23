package vn.bank.account.domain;

import vn.bank.common.error.ErrorCode;

public enum AccountErrorCode implements ErrorCode {
    CUSTOMER_NOT_FOUND(404),
    CUSTOMER_NOT_ACTIVE(422),
    ACCOUNT_LIMIT_EXCEEDED(422),
    ACCOUNT_TYPE_NOT_ALLOWED(422),
    CUSTOMER_SERVICE_UNAVAILABLE(503),
    ACCESS_DENIED(403),
    ACCOUNT_NOT_FOUND(404),
    ACCOUNT_ACCESS_DENIED(403),
    INVALID_STATUS_TRANSITION(422),
    ACCOUNT_BALANCE_NOT_ZERO(422),
    CONCURRENT_MODIFICATION(409);

    private final int httpStatus;

    AccountErrorCode(int httpStatus) {
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
