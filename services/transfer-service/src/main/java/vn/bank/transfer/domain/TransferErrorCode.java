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
    IDEMPOTENCY_STORE_UNAVAILABLE(503),
    // Lenh khong ton tai hoac khong thuoc khach hang dang dang nhap (khong phan biet de khong lo thong tin)
    TRANSFER_NOT_FOUND(404),
    // Lenh khong o trang thai OTP_PENDING: da xac thuc OTP, da FAILED hoac da xu ly xong
    TRANSFER_NOT_AWAITING_OTP(422),
    OTP_EXPIRED(422),
    OTP_INVALID(422),
    // Sai OTP den so lan toi da: lenh FAILED voi FAILURE_CODE = OTP_MAX_ATTEMPTS
    OTP_MAX_ATTEMPTS(422),
    // Gui lai OTP chua du khoang cach toi thieu tu lan gui truoc
    OTP_RESEND_TOO_SOON(429),
    OTP_RESEND_LIMIT_EXCEEDED(422),
    // Redis loi o luong OTP bat buoc (muc 12 convention)
    OTP_STORE_UNAVAILABLE(503),
    // So tien lenh vuot han muc moi lenh cua loai khach hang (BANK-043)
    PER_TRANSACTION_LIMIT_EXCEEDED(422),
    // Tong da chuyen trong ngay cong lenh nay vuot han muc ngay; luc xac thuc OTP thi lenh FAILED voi ma nay
    DAILY_LIMIT_EXCEEDED(422),
    // Redis loi khi giu cho han muc ngay (luong bat buoc, muc 12 convention)
    TRANSFER_LIMIT_STORE_UNAVAILABLE(503);

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
