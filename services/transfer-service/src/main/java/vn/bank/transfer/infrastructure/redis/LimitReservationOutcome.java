package vn.bank.transfer.infrastructure.redis;

public enum LimitReservationOutcome {
    RESERVED,
    // Tong da giu trong ngay cong so tien nay vuot han muc ngay: khong cong gi
    LIMIT_EXCEEDED,
    // Chua co key han muc cua ngay (dau ngay, het TTL, Redis mat du lieu): tinh lai tu DB roi giu cho lai
    NOT_INITIALIZED
}
