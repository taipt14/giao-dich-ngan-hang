-- Doi soat cuoi ngay (BANK-034): (1) moi journal can No = Co; (2) BALANCE moi tai khoan = tong so cai cua tai khoan do.
-- Chay lai cung ngay ghi de ket qua cu bang MERGE theo khoa nghiep vu, khong xoa dong nao: loi khong con thi danh dau
-- RESOLVED, nen dong OPEN cua mot ngay luon dung bang ket qua lan chay moi nhat.
CREATE SEQUENCE SEQ_RECONCILE_RESULT START WITH 1 INCREMENT BY 1 NOCYCLE;
CREATE SEQUENCE SEQ_RECONCILE_RUN START WITH 1 INCREMENT BY 1 NOCYCLE;

CREATE TABLE RECONCILE_RESULT (
    RECONCILE_RESULT_ID NUMBER(19)    NOT NULL,
    BUSINESS_DATE       DATE          NOT NULL,
    ERROR_TYPE          VARCHAR2(30)  NOT NULL,
    ACCOUNT_NO          VARCHAR2(20),
    JOURNAL_ID          NUMBER(19),
    EXPECTED_VALUE      NUMBER(19, 2) NOT NULL,
    ACTUAL_VALUE        NUMBER(19, 2) NOT NULL,
    STATUS              VARCHAR2(10)  NOT NULL,
    RUN_ID              NUMBER(19)    NOT NULL,
    DETECTED_AT         TIMESTAMP     NOT NULL,
    CHECKED_AT          TIMESTAMP     NOT NULL,
    CONSTRAINT PK_RECONCILE_RESULT PRIMARY KEY (RECONCILE_RESULT_ID),
    -- Moi loi chi mot dong cho mot ngay: Oracle coi hai khoa co cung gia tri khac NULL (cot con lai NULL) la trung
    CONSTRAINT UK_RECONCILE_RESULT_DATE_TYPE_ACC_JRN UNIQUE (BUSINESS_DATE, ERROR_TYPE, ACCOUNT_NO, JOURNAL_ID),
    CONSTRAINT FK_RECONCILE_RESULT_ACCOUNT_NO FOREIGN KEY (ACCOUNT_NO) REFERENCES ACCOUNT (ACCOUNT_NO),
    CONSTRAINT FK_RECONCILE_RESULT_JOURNAL_ID FOREIGN KEY (JOURNAL_ID) REFERENCES JOURNAL (JOURNAL_ID),
    CONSTRAINT CK_RECONCILE_RESULT_ERROR_TYPE CHECK (ERROR_TYPE IN ('BALANCE_MISMATCH', 'JOURNAL_UNBALANCED')),
    CONSTRAINT CK_RECONCILE_RESULT_TARGET CHECK (
        (ERROR_TYPE = 'BALANCE_MISMATCH' AND ACCOUNT_NO IS NOT NULL AND JOURNAL_ID IS NULL)
        OR (ERROR_TYPE = 'JOURNAL_UNBALANCED' AND JOURNAL_ID IS NOT NULL AND ACCOUNT_NO IS NULL)
    ),
    CONSTRAINT CK_RECONCILE_RESULT_STATUS CHECK (STATUS IN ('OPEN', 'RESOLVED'))
);

-- Doi soat lay journal ghi trong ngay nghiep vu; khong co index thi moi ngay quet toan bo JOURNAL
CREATE INDEX IX_JOURNAL_CREATED_AT ON JOURNAL (CREATED_AT);

COMMENT ON TABLE RECONCILE_RESULT IS 'Loi doi soat cuoi ngay (PKG_RECONCILE.RUN), moi loi mot dong cho moi ngay nghiep vu';
COMMENT ON COLUMN RECONCILE_RESULT.RECONCILE_RESULT_ID IS 'Khoa chinh, lay tu SEQ_RECONCILE_RESULT';
COMMENT ON COLUMN RECONCILE_RESULT.BUSINESS_DATE IS 'Ngay nghiep vu doi soat (gio Asia/Ho_Chi_Minh)';
COMMENT ON COLUMN RECONCILE_RESULT.ERROR_TYPE IS 'BALANCE_MISMATCH: BALANCE khac tong so cai; JOURNAL_UNBALANCED: tong No khac tong Co';
COMMENT ON COLUMN RECONCILE_RESULT.ACCOUNT_NO IS 'Tai khoan lech so du (BALANCE_MISMATCH), NULL voi loai khac';
COMMENT ON COLUMN RECONCILE_RESULT.JOURNAL_ID IS 'Journal khong can (JOURNAL_UNBALANCED), NULL voi loai khac';
COMMENT ON COLUMN RECONCILE_RESULT.EXPECTED_VALUE IS 'BALANCE_MISMATCH: tong so cai (Co tru No); JOURNAL_UNBALANCED: tong No';
COMMENT ON COLUMN RECONCILE_RESULT.ACTUAL_VALUE IS 'BALANCE_MISMATCH: ACCOUNT.BALANCE; JOURNAL_UNBALANCED: tong Co';
COMMENT ON COLUMN RECONCILE_RESULT.STATUS IS 'OPEN: lan chay moi nhat cua ngay van thay loi; RESOLVED: lan chay moi nhat khong con thay';
COMMENT ON COLUMN RECONCILE_RESULT.RUN_ID IS 'Lan chay gan nhat kiem tra dong nay, lay tu SEQ_RECONCILE_RUN';
COMMENT ON COLUMN RECONCILE_RESULT.DETECTED_AT IS 'Thoi diem lan dau phat hien loi (UTC)';
COMMENT ON COLUMN RECONCILE_RESULT.CHECKED_AT IS 'Thoi diem lan chay gan nhat kiem tra dong nay (UTC)';

CREATE OR REPLACE PACKAGE PKG_RECONCILE AS
    -- Doi soat ngay p_business_date (phan gio bi bo). Khong COMMIT: transaction cua nguoi goi quyet dinh
    PROCEDURE RUN(p_business_date IN DATE);
END PKG_RECONCILE;
/

CREATE OR REPLACE PACKAGE BODY PKG_RECONCILE AS

    -- Cung mui gio voi BankZone.ZONE (muc 4.2 convention); CREATED_AT luu UTC
    c_bank_zone CONSTANT VARCHAR2(30) := 'Asia/Ho_Chi_Minh';
    c_open CONSTANT VARCHAR2(10) := 'OPEN';

    PROCEDURE check_journals(p_date IN DATE, p_run_id IN NUMBER, p_now IN TIMESTAMP) IS
        v_from TIMESTAMP := SYS_EXTRACT_UTC(FROM_TZ(CAST(p_date AS TIMESTAMP), c_bank_zone));
        v_to   TIMESTAMP := SYS_EXTRACT_UTC(FROM_TZ(CAST(p_date + 1 AS TIMESTAMP), c_bank_zone));
    BEGIN
        MERGE INTO RECONCILE_RESULT r
        USING (
            SELECT j.JOURNAL_ID,
                   NVL(SUM(CASE WHEN e.DIRECTION = 'DEBIT' THEN e.AMOUNT END), 0) AS TOTAL_DEBIT,
                   NVL(SUM(CASE WHEN e.DIRECTION = 'CREDIT' THEN e.AMOUNT END), 0) AS TOTAL_CREDIT
            FROM JOURNAL j
            LEFT JOIN LEDGER_ENTRY e ON e.JOURNAL_ID = j.JOURNAL_ID
            WHERE j.CREATED_AT >= v_from AND j.CREATED_AT < v_to
            GROUP BY j.JOURNAL_ID
            HAVING NVL(SUM(CASE WHEN e.DIRECTION = 'DEBIT' THEN e.AMOUNT END), 0)
                <> NVL(SUM(CASE WHEN e.DIRECTION = 'CREDIT' THEN e.AMOUNT END), 0)
        ) s
        ON (r.BUSINESS_DATE = p_date AND r.ERROR_TYPE = 'JOURNAL_UNBALANCED' AND r.JOURNAL_ID = s.JOURNAL_ID)
        WHEN MATCHED THEN UPDATE SET
            r.EXPECTED_VALUE = s.TOTAL_DEBIT, r.ACTUAL_VALUE = s.TOTAL_CREDIT, r.STATUS = c_open,
            r.RUN_ID = p_run_id, r.CHECKED_AT = p_now
        WHEN NOT MATCHED THEN INSERT (
            RECONCILE_RESULT_ID, BUSINESS_DATE, ERROR_TYPE, ACCOUNT_NO, JOURNAL_ID, EXPECTED_VALUE, ACTUAL_VALUE,
            STATUS, RUN_ID, DETECTED_AT, CHECKED_AT
        ) VALUES (
            SEQ_RECONCILE_RESULT.NEXTVAL, p_date, 'JOURNAL_UNBALANCED', NULL, s.JOURNAL_ID, s.TOTAL_DEBIT,
            s.TOTAL_CREDIT, c_open, p_run_id, p_now, p_now
        );
    END check_journals;

    -- So du la anh chup luc chay (ACCOUNT chi giu so du hien tai). Mot cau lenh nen Oracle doc nhat quan ACCOUNT va
    -- LEDGER_ENTRY tai cung thoi diem: posting commit giua chung khong tao lech gia
    PROCEDURE check_balances(p_date IN DATE, p_run_id IN NUMBER, p_now IN TIMESTAMP) IS
    BEGIN
        MERGE INTO RECONCILE_RESULT r
        USING (
            SELECT a.ACCOUNT_NO, NVL(l.LEDGER_BALANCE, 0) AS LEDGER_BALANCE, a.BALANCE
            FROM ACCOUNT a
            LEFT JOIN (
                SELECT ACCOUNT_NO, SUM(CASE WHEN DIRECTION = 'CREDIT' THEN AMOUNT ELSE -AMOUNT END) AS LEDGER_BALANCE
                FROM LEDGER_ENTRY
                GROUP BY ACCOUNT_NO
            ) l ON l.ACCOUNT_NO = a.ACCOUNT_NO
            WHERE a.BALANCE <> NVL(l.LEDGER_BALANCE, 0)
        ) s
        ON (r.BUSINESS_DATE = p_date AND r.ERROR_TYPE = 'BALANCE_MISMATCH' AND r.ACCOUNT_NO = s.ACCOUNT_NO)
        WHEN MATCHED THEN UPDATE SET
            r.EXPECTED_VALUE = s.LEDGER_BALANCE, r.ACTUAL_VALUE = s.BALANCE, r.STATUS = c_open,
            r.RUN_ID = p_run_id, r.CHECKED_AT = p_now
        WHEN NOT MATCHED THEN INSERT (
            RECONCILE_RESULT_ID, BUSINESS_DATE, ERROR_TYPE, ACCOUNT_NO, JOURNAL_ID, EXPECTED_VALUE, ACTUAL_VALUE,
            STATUS, RUN_ID, DETECTED_AT, CHECKED_AT
        ) VALUES (
            SEQ_RECONCILE_RESULT.NEXTVAL, p_date, 'BALANCE_MISMATCH', s.ACCOUNT_NO, NULL, s.LEDGER_BALANCE,
            s.BALANCE, c_open, p_run_id, p_now, p_now
        );
    END check_balances;

    PROCEDURE RUN(p_business_date IN DATE) IS
        v_date   DATE := TRUNC(p_business_date);
        v_now    TIMESTAMP := SYS_EXTRACT_UTC(SYSTIMESTAMP);
        v_run_id NUMBER(19);
    BEGIN
        IF p_business_date IS NULL THEN
            RAISE_APPLICATION_ERROR(-20010, 'p_business_date is required');
        END IF;
        -- Hai lan chay dong thoi (lich va thu cong) se cung INSERT mot khoa → loi UK; khoa bang de chay noi tiep.
        -- EXCLUSIVE van cho phep SELECT, khoa nha khi transaction cua nguoi goi ket thuc
        LOCK TABLE RECONCILE_RESULT IN EXCLUSIVE MODE;
        v_run_id := SEQ_RECONCILE_RUN.NEXTVAL;

        check_journals(v_date, v_run_id, v_now);
        check_balances(v_date, v_run_id, v_now);

        -- Loi lan truoc ma lan nay khong con thay: giu lai lich su, chi danh dau da het
        UPDATE RECONCILE_RESULT
        SET STATUS = 'RESOLVED', RUN_ID = v_run_id, CHECKED_AT = v_now
        WHERE BUSINESS_DATE = v_date AND STATUS = c_open AND RUN_ID <> v_run_id;
    END RUN;

END PKG_RECONCILE;
/
