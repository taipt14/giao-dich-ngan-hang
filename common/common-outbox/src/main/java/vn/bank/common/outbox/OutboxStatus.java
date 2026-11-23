package vn.bank.common.outbox;

/** Trang thai mot dong {@code OUTBOX_EVENT}. */
enum OutboxStatus {
    /** Chua gui (hoac gui loi, con duoc thu lai). */
    NEW,
    /** Kafka da xac nhan. */
    SENT,
    /** Gui loi qua so lan cho phep, can nguoi kiem tra va gui lai. */
    FAILED
}
