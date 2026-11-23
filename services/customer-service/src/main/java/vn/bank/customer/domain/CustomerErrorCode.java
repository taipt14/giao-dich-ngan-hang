package vn.bank.customer.domain;

import vn.bank.common.error.ErrorCode;

public enum CustomerErrorCode implements ErrorCode {
    CUSTOMER_NOT_FOUND(404),
    CUSTOMER_ID_NUMBER_DUPLICATED(409),
    CUSTOMER_PHONE_DUPLICATED(409),
    CUSTOMER_UNDERAGE(400),
    CUSTOMER_STATUS_UNCHANGED(422),
    CONCURRENT_MODIFICATION(409),
    ACCESS_DENIED(403);

    private final int httpStatus;

    CustomerErrorCode(int httpStatus) {
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
