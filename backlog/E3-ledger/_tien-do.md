# Tiến độ epic E3

| Task | Kết quả | Commit | Số test | Ghi chú |
|---|---|---|---|---|
| BANK-030 | BLOCKED | — | (code dở trong working tree, subagent báo 309 pass) | Migration V4 có `DROP CONSTRAINT` CK_ACCOUNT_TYPE, CK_ACCOUNT_BALANCE (add lại cùng tên, nới cho GL) và `MODIFY (CIF NULL)` → điều kiện dừng "migration có DROP"; đổi hành vi BANK-020 (accountType GL: 400→422) và BANK-021 (số dư GL → 403). Cần người duyệt |
| BANK-030 | DONE | feat(BANK-030) | +16, toàn repo 309 pass | Người duyệt đồng ý V4 (DROP + ADD lại CK_ACCOUNT_TYPE/CK_ACCOUNT_BALANCE cùng tên, CIF nullable + CK_ACCOUNT_CIF); GL: CASH_VAULT/FEE_INCOME/NAPAS_SETTLEMENT, CIF NULL; trigger chặn UPDATE/DELETE LEDGER_ENTRY; PK ENTRY_ID theo task; docs/ledger.md |
| BANK-031 | DONE | feat(BANK-031) | +32, toàn repo 341 pass | POST /internal/postings: 201 mới / 200 trả journal cũ (không so body); khóa tài khoản PESSIMISTIC_WRITE theo thứ tự ACCOUNT_NO (chưa timeout, để BANK-032); trùng reference đồng thời → bắt UK đọc lại; INSUFFICIENT_BALANCE 422; AVAILABLE_BALANCE đi cùng BALANCE; evict cache mọi tài khoản; BalanceChanged gộp ròng theo tài khoản khách; thêm field description |
| BANK-032 | DONE | feat(BANK-032) | +6, toàn repo 347 pass | Khóa từng tài khoản FOR UPDATE WAIT 5 theo ACCOUNT_NO tăng dần (thay findAllForUpdate của BANK-031, API không đổi); hết thời gian chờ/deadlock → 503 ACCOUNT_BUSY (TechnicalException, bị log ERROR); chọn pessimistic thay optimistic |
| BANK-033 | BLOCKED | — | — | Task mâu thuẫn convention: (1) `page` vs keyset ENTRY_ID; (2) số tiền +/- vs DIRECTION (conv 4.1); (3) chưa rõ from/to (LocalDate giờ VN?), cách tính 90 ngày, mặc định 30 ngày, from>to. Chưa sửa code |
