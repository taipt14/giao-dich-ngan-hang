package vn.bank.transfer.domain;

import vn.bank.common.error.ErrorCode;

public enum TransferErrorCode implements ErrorCode {
    INVALID_AMOUNT(400),
    INVALID_STATUS_TRANSITION(422);

    private final int httpStatus;

    TransferErrorCode(int httpStatus) {
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
