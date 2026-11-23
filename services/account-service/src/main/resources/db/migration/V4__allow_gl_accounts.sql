-- Tai khoan noi bo (GL) nam chung bang ACCOUNT de thay doi so du va ghi but toan cung mot transaction (kien-truc muc 3).
-- Oracle khong sua duoc dieu kien cua CHECK: phai bo constraint cu roi tao lai cung ten. Chi doi rang buoc, khong
-- xoa du lieu hay cot nao.

-- Loai GL: tai khoan cua chinh ngan hang (quy tien mat, thu phi, thanh toan bu tru NAPAS)
ALTER TABLE ACCOUNT DROP CONSTRAINT CK_ACCOUNT_TYPE;
ALTER TABLE ACCOUNT ADD CONSTRAINT CK_ACCOUNT_TYPE CHECK (ACCOUNT_TYPE IN ('CURRENT', 'GL'));

-- Tai khoan GL duoc phep am (vi du CASH_VAULT am khi khach nap tien); so du khong am chi ap dung cho tai khoan khach hang
ALTER TABLE ACCOUNT DROP CONSTRAINT CK_ACCOUNT_BALANCE;
ALTER TABLE ACCOUNT ADD CONSTRAINT CK_ACCOUNT_BALANCE CHECK (ACCOUNT_TYPE = 'GL' OR BALANCE >= 0);

-- Tai khoan GL khong thuoc khach hang nao: CIF bat buoc NULL voi GL va bat buoc co voi tai khoan khach hang,
-- de khong co CIF gia nao lot vao truy van theo CIF (dem gioi han 3 tai khoan, liet ke, khoa theo khach hang)
ALTER TABLE ACCOUNT MODIFY (CIF NULL);
ALTER TABLE ACCOUNT ADD CONSTRAINT CK_ACCOUNT_CIF CHECK (
    (ACCOUNT_TYPE = 'GL' AND CIF IS NULL) OR (ACCOUNT_TYPE <> 'GL' AND CIF IS NOT NULL)
);

-- Ma tai khoan GL la chuoi chu, khong trung duoc voi so tai khoan khach hang (12 chu so)
INSERT INTO ACCOUNT (ACCOUNT_NO, CIF, ACCOUNT_TYPE, CURRENCY, BALANCE, AVAILABLE_BALANCE, STATUS, VERSION, CREATED_AT, UPDATED_AT)
VALUES ('CASH_VAULT', NULL, 'GL', 'VND', 0, 0, 'ACTIVE', 0, SYS_EXTRACT_UTC(SYSTIMESTAMP), SYS_EXTRACT_UTC(SYSTIMESTAMP));
INSERT INTO ACCOUNT (ACCOUNT_NO, CIF, ACCOUNT_TYPE, CURRENCY, BALANCE, AVAILABLE_BALANCE, STATUS, VERSION, CREATED_AT, UPDATED_AT)
VALUES ('FEE_INCOME', NULL, 'GL', 'VND', 0, 0, 'ACTIVE', 0, SYS_EXTRACT_UTC(SYSTIMESTAMP), SYS_EXTRACT_UTC(SYSTIMESTAMP));
INSERT INTO ACCOUNT (ACCOUNT_NO, CIF, ACCOUNT_TYPE, CURRENCY, BALANCE, AVAILABLE_BALANCE, STATUS, VERSION, CREATED_AT, UPDATED_AT)
VALUES ('NAPAS_SETTLEMENT', NULL, 'GL', 'VND', 0, 0, 'ACTIVE', 0, SYS_EXTRACT_UTC(SYSTIMESTAMP), SYS_EXTRACT_UTC(SYSTIMESTAMP));

COMMENT ON TABLE ACCOUNT IS 'Tai khoan thanh toan cua khach hang (CURRENT) va tai khoan noi bo cua ngan hang (GL)';
COMMENT ON COLUMN ACCOUNT.ACCOUNT_NO IS 'So tai khoan khach hang: 1000 + 8 chu so tu SEQ_ACCOUNT_NO; tai khoan GL: ma chu (CASH_VAULT...)';
COMMENT ON COLUMN ACCOUNT.CIF IS 'Ma khach hang chu tai khoan (customer-service); NULL voi tai khoan GL';
COMMENT ON COLUMN ACCOUNT.ACCOUNT_TYPE IS 'Loai tai khoan: CURRENT (thanh toan), GL (noi bo, duoc phep am)';
