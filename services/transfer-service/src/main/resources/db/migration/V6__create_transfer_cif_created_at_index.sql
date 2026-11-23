-- Tra cuu lich su lenh cua khach hang (BANK-047): loc theo CIF, khoang CREATED_AT, sap xep CREATED_AT giam dan
CREATE INDEX IX_TRANSFER_CIF_CREATED_AT ON TRANSFER (CIF, CREATED_AT);
