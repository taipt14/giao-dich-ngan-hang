-- So cai but toan kep, giai thich but toan tung loai giao dich: docs/ledger.md
CREATE SEQUENCE SEQ_JOURNAL START WITH 1 INCREMENT BY 1 NOCYCLE;
CREATE SEQUENCE SEQ_LEDGER_ENTRY START WITH 1 INCREMENT BY 1 NOCYCLE;

CREATE TABLE JOURNAL (
    JOURNAL_ID     NUMBER(19)         NOT NULL,
    REFERENCE_TYPE VARCHAR2(20)       NOT NULL,
    -- 64: du cho transferId (UUID 36 ky tu) va Idempotency-Key cua nap tien
    REFERENCE_ID   VARCHAR2(64)       NOT NULL,
    -- 255 CHAR: mo ta chuyen khoan toi da 210 ky tu (co dau tieng Viet) cong tien to do he thong them
    DESCRIPTION    VARCHAR2(255 CHAR),
    CREATED_AT     TIMESTAMP          NOT NULL,
    CONSTRAINT PK_JOURNAL PRIMARY KEY (JOURNAL_ID),
    -- Posting idempotent theo (REFERENCE_TYPE, REFERENCE_ID) (BANK-031). REFERENCE_ID dat truoc de index nay phuc vu
    -- ca tra soat chi theo referenceId (GET /internal/postings?referenceId=, BANK-045)
    CONSTRAINT UK_JOURNAL_REFERENCE_ID_TYPE UNIQUE (REFERENCE_ID, REFERENCE_TYPE),
    CONSTRAINT CK_JOURNAL_REFERENCE_TYPE CHECK (REFERENCE_TYPE IN ('DEPOSIT', 'TRANSFER', 'FEE', 'REVERSAL'))
);

CREATE TABLE LEDGER_ENTRY (
    ENTRY_ID      NUMBER(19)    NOT NULL,
    JOURNAL_ID    NUMBER(19)    NOT NULL,
    ACCOUNT_NO    VARCHAR2(20)  NOT NULL,
    DIRECTION     VARCHAR2(6)   NOT NULL,
    AMOUNT        NUMBER(19, 2) NOT NULL,
    -- Khong rang buoc >= 0: tai khoan GL duoc phep am (V4)
    BALANCE_AFTER NUMBER(19, 2) NOT NULL,
    CREATED_AT    TIMESTAMP     NOT NULL,
    CONSTRAINT PK_LEDGER_ENTRY PRIMARY KEY (ENTRY_ID),
    CONSTRAINT FK_LEDGER_ENTRY_JOURNAL_ID FOREIGN KEY (JOURNAL_ID) REFERENCES JOURNAL (JOURNAL_ID),
    CONSTRAINT FK_LEDGER_ENTRY_ACCOUNT_NO FOREIGN KEY (ACCOUNT_NO) REFERENCES ACCOUNT (ACCOUNT_NO),
    CONSTRAINT CK_LEDGER_ENTRY_DIRECTION CHECK (DIRECTION IN ('DEBIT', 'CREDIT')),
    -- Huong tien the hien bang DIRECTION, khong bang so am (muc 4.1 convention)
    CONSTRAINT CK_LEDGER_ENTRY_AMOUNT CHECK (AMOUNT > 0)
);

-- Sao ke theo tai khoan va khoang thoi gian (BANK-033)
CREATE INDEX IX_LEDGER_ENTRY_ACC_CREATED ON LEDGER_ENTRY (ACCOUNT_NO, CREATED_AT);
-- Lay cac dong cua mot journal (posting trung tra lai journal cu); Oracle khong tu tao index cho FK
CREATE INDEX IX_LEDGER_ENTRY_JOURNAL_ID ON LEDGER_ENTRY (JOURNAL_ID);

-- So cai chi ghi them. Khong dung REVOKE vi ACCOUNT_SVC la chu bang (owner luon co moi quyen tren bang cua minh),
-- nen chan bang trigger muc cau lenh: chan ca UPDATE/DELETE khong khop dong nao. Sai thi ghi but toan dao (REVERSAL).
CREATE OR REPLACE TRIGGER TRG_LEDGER_ENTRY_IMMUTABLE
    BEFORE UPDATE OR DELETE ON LEDGER_ENTRY
BEGIN
    RAISE_APPLICATION_ERROR(-20001, 'LEDGER_ENTRY is append-only: UPDATE and DELETE are not allowed');
END;
/

COMMENT ON TABLE JOURNAL IS 'But toan: mot nghiep vu lam thay doi so du, gom cac dong LEDGER_ENTRY co tong No = tong Co';
COMMENT ON COLUMN JOURNAL.JOURNAL_ID IS 'Khoa chinh, lay tu SEQ_JOURNAL';
COMMENT ON COLUMN JOURNAL.REFERENCE_TYPE IS 'Loai nghiep vu goc: DEPOSIT, TRANSFER, FEE, REVERSAL';
COMMENT ON COLUMN JOURNAL.REFERENCE_ID IS 'Id nghiep vu goc (vi du transferId), duy nhat cung REFERENCE_TYPE';
COMMENT ON COLUMN JOURNAL.DESCRIPTION IS 'Mo ta hien thi tren sao ke';
COMMENT ON COLUMN JOURNAL.CREATED_AT IS 'Thoi diem ghi but toan (UTC)';

COMMENT ON TABLE LEDGER_ENTRY IS 'Dong so cai: mot ve No hoac Co cua mot tai khoan trong but toan. Chi ghi them';
COMMENT ON COLUMN LEDGER_ENTRY.ENTRY_ID IS 'Khoa chinh, lay tu SEQ_LEDGER_ENTRY; tang dan, dung phan trang keyset sao ke';
COMMENT ON COLUMN LEDGER_ENTRY.JOURNAL_ID IS 'But toan chua dong nay';
COMMENT ON COLUMN LEDGER_ENTRY.ACCOUNT_NO IS 'Tai khoan khach hang hoac tai khoan GL';
COMMENT ON COLUMN LEDGER_ENTRY.DIRECTION IS 'DEBIT (No: tien ra khoi tai khoan) hoac CREDIT (Co: tien vao tai khoan)';
COMMENT ON COLUMN LEDGER_ENTRY.AMOUNT IS 'So tien, luon > 0';
COMMENT ON COLUMN LEDGER_ENTRY.BALANCE_AFTER IS 'So du cua tai khoan ngay sau dong nay';
COMMENT ON COLUMN LEDGER_ENTRY.CREATED_AT IS 'Thoi diem ghi (UTC), bang JOURNAL.CREATED_AT';
