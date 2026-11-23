package vn.bank.customer.domain;

import vn.bank.common.error.ErrorCode;

public enum CustomerErrorCode implements ErrorCode {
    CUSTOMER_ID_NUMBER_DUPLICATED(409),
    CUSTOMER_PHONE_DUPLICATED(409),
    CUSTOMER_UNDERAGE(400),
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
