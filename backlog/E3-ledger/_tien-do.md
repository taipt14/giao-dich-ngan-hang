# Tiến độ epic E3

| Task | Kết quả | Commit | Số test | Ghi chú |
|---|---|---|---|---|
| BANK-030 | BLOCKED | — | (code dở trong working tree, subagent báo 309 pass) | Migration V4 có `DROP CONSTRAINT` CK_ACCOUNT_TYPE, CK_ACCOUNT_BALANCE (add lại cùng tên, nới cho GL) và `MODIFY (CIF NULL)` → điều kiện dừng "migration có DROP"; đổi hành vi BANK-020 (accountType GL: 400→422) và BANK-021 (số dư GL → 403). Cần người duyệt |
