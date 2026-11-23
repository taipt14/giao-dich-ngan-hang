package vn.bank.transfer.domain;

import vn.bank.common.error.ErrorCode;

public enum TransferErrorCode implements ErrorCode {
    INVALID_AMOUNT(400),
    INVALID_STATUS_TRANSITION(422),
    // Header Idempotency-Key bat buoc khi tao lenh, dinh dang UUID
    IDEMPOTENCY_KEY_REQUIRED(400),
    IDEMPOTENCY_KEY_INVALID(400),
    // Idempotency-Key da dung cho mot lenh khac noi dung
    IDEMPOTENCY_KEY_REUSED(422),
    // Request khac cung Idempotency-Key dang xu ly, chua tao xong lenh: client gui lai sau
    IDEMPOTENCY_KEY_IN_PROGRESS(409),
    // Gateway khong dat header X-Roles / X-Cif phu hop (user khong phai khach hang)
    ACCESS_DENIED(403),
    // Tai khoan nguon khong ton tai hoac khong thuoc khach hang dang dang nhap (khong phan biet de khong lo thong tin)
    SOURCE_ACCOUNT_ACCESS_DENIED(403),
    DEST_ACCOUNT_NOT_FOUND(422),
    DEST_ACCOUNT_NOT_ACTIVE(422),
    SAME_ACCOUNT_TRANSFER(422),
    ACCOUNT_SERVICE_UNAVAILABLE(503),
    IDEMPOTENCY_STORE_UNAVAILABLE(503);

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
