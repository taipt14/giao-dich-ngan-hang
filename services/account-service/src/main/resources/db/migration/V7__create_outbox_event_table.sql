-- Outbox pattern (BANK-046): event nghiep vu ghi cung transaction voi du lieu, OutboxPublisherJob gui len Kafka
CREATE TABLE OUTBOX_EVENT (
    EVENT_ID       VARCHAR2(36)  NOT NULL,
    AGGREGATE_TYPE VARCHAR2(50)  NOT NULL,
    AGGREGATE_ID   VARCHAR2(100) NOT NULL,
    TOPIC          VARCHAR2(249) NOT NULL,
    EVENT_TYPE     VARCHAR2(100) NOT NULL,
    PAYLOAD        CLOB          NOT NULL,
    STATUS         VARCHAR2(10)  NOT NULL,
    RETRY_COUNT    NUMBER(3)     DEFAULT 0 NOT NULL,
    CREATED_AT     TIMESTAMP     NOT NULL,
    SENT_AT        TIMESTAMP,
    CONSTRAINT PK_OUTBOX_EVENT PRIMARY KEY (EVENT_ID),
    CONSTRAINT CK_OUTBOX_EVENT_STATUS CHECK (STATUS IN ('NEW', 'SENT', 'FAILED')),
    CONSTRAINT CK_OUTBOX_EVENT_RETRY_COUNT CHECK (RETRY_COUNT >= 0),
    CONSTRAINT CK_OUTBOX_EVENT_SENT_AT CHECK (STATUS <> 'SENT' OR SENT_AT IS NOT NULL)
);

-- Publisher quet dong NEW cu nhat; job don xoa dong SENT theo SENT_AT
CREATE INDEX IX_OUTBOX_EVENT_STATUS_CREATED ON OUTBOX_EVENT (STATUS, CREATED_AT);
CREATE INDEX IX_OUTBOX_EVENT_STATUS_SENT ON OUTBOX_EVENT (STATUS, SENT_AT);

COMMENT ON TABLE OUTBOX_EVENT IS 'Event cho gui Kafka (outbox pattern): ghi cung transaction nghiep vu, gui khi va chi khi transaction commit';
COMMENT ON COLUMN OUTBOX_EVENT.EVENT_ID IS 'eventId (UUID) trong EventEnvelope';
COMMENT ON COLUMN OUTBOX_EVENT.AGGREGATE_TYPE IS 'Loai aggregate phat event, vi du CUSTOMER, ACCOUNT, TRANSFER';
COMMENT ON COLUMN OUTBOX_EVENT.AGGREGATE_ID IS 'Id aggregate, dung lam key message Kafka de giu thu tu theo aggregate';
COMMENT ON COLUMN OUTBOX_EVENT.TOPIC IS 'Topic Kafka se gui event';
COMMENT ON COLUMN OUTBOX_EVENT.EVENT_TYPE IS 'eventType trong EventEnvelope, vi du BalanceChanged';
COMMENT ON COLUMN OUTBOX_EVENT.PAYLOAD IS 'JSON cua ca EventEnvelope, gui nguyen van lam value message';
COMMENT ON COLUMN OUTBOX_EVENT.STATUS IS 'NEW: chua gui; SENT: Kafka da xac nhan; FAILED: gui loi qua so lan cho phep, can xu ly tay';
COMMENT ON COLUMN OUTBOX_EVENT.RETRY_COUNT IS 'So lan gui loi';
COMMENT ON COLUMN OUTBOX_EVENT.CREATED_AT IS 'Thoi diem ghi event (UTC), thu tu gui';
COMMENT ON COLUMN OUTBOX_EVENT.SENT_AT IS 'Thoi diem Kafka xac nhan (UTC); dong SENT cu hon 7 ngay bi xoa';
