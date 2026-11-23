# Sổ cái bút toán kép (ledger)

> Schema: `account-service`, migration `V4__allow_gl_accounts.sql`, `V5__create_journal_and_ledger_entry_tables.sql` (BANK-030).
> API ghi bút toán: `POST /internal/postings` (BANK-031).

## 1. Bảng

| Bảng | Vai trò |
|---|---|
| `JOURNAL` | Một bút toán = một nghiệp vụ làm thay đổi số dư. Duy nhất theo `(REFERENCE_TYPE, REFERENCE_ID)` để posting idempotent |
| `LEDGER_ENTRY` | Một dòng Nợ hoặc Có của một tài khoản trong bút toán, kèm `BALANCE_AFTER`. **Chỉ ghi thêm**: trigger `TRG_LEDGER_ENTRY_IMMUTABLE` chặn mọi `UPDATE`/`DELETE` (ORA-20001) |
| `ACCOUNT` | Chứa cả tài khoản khách hàng (`CURRENT`) và tài khoản nội bộ (`GL`), để đổi số dư và ghi ledger trong cùng một transaction |

`REFERENCE_TYPE`: `DEPOSIT`, `TRANSFER`, `FEE`, `REVERSAL` (CHECK `CK_JOURNAL_REFERENCE_TYPE`).

## 2. Quy ước Nợ / Có

Mọi tài khoản (khách hàng và GL) dùng **cùng một quy ước**:

| Hướng | Ý nghĩa | Số dư |
|---|---|---|
| `DEBIT` (Nợ) | Tiền đi ra khỏi tài khoản | `BALANCE` giảm `AMOUNT` |
| `CREDIT` (Có) | Tiền đi vào tài khoản | `BALANCE` tăng `AMOUNT` |

Quy tắc bắt buộc của mỗi journal (kiểm tra trong BANK-031):
- Có ít nhất 1 dòng `DEBIT` và 1 dòng `CREDIT`.
- Tổng `DEBIT` = tổng `CREDIT`.
- Mọi `AMOUNT > 0` (CHECK `CK_LEDGER_ENTRY_AMOUNT`); hướng tiền chỉ thể hiện bằng `DIRECTION`.
- `BALANCE_AFTER` = số dư tài khoản ngay sau dòng đó; dòng cuối của tài khoản = `ACCOUNT.BALANCE`.

Hệ quả: tổng `BALANCE` của **mọi** tài khoản (khách hàng + GL) luôn bằng 0. Đây là bất biến để đối soát (BANK-034).
Vì tiền khách hàng có phải "đến từ đâu đó", tài khoản GL thường mang số dư âm. Do đó `CK_ACCOUNT_BALANCE` chỉ bắt số dư ≥ 0 với tài khoản khách hàng.

## 3. Tài khoản nội bộ (GL)

Tạo sẵn bằng migration V4, `ACCOUNT_TYPE = 'GL'`, `CIF = NULL` (CHECK `CK_ACCOUNT_CIF`), `ACTIVE`, `VND`, số dư ban đầu 0. Không mở được qua API (`ACCOUNT_TYPE_NOT_ALLOWED`).

| Mã | Ý nghĩa | Số dư thường |
|---|---|---|
| `CASH_VAULT` | Quỹ tiền mặt tại quầy | Âm: tổng tiền mặt khách đã nộp vào |
| `FEE_INCOME` | Thu nhập phí | Dương: tổng phí đã thu |
| `NAPAS_SETTLEMENT` | Thanh toán bù trừ với NAPAS | Dương: số tiền ngân hàng còn phải chuyển cho NAPAS (chờ quyết toán) |

## 4. Bút toán theo loại giao dịch

Ví dụ: khách A, B là tài khoản khách hàng; số tiền `amount`, phí `fee`.

### 4.1. Nạp tiền tại quầy (BANK-023)
`REFERENCE_TYPE = DEPOSIT`, `REFERENCE_ID` = mã nghiệp vụ nạp tiền (Idempotency-Key).

| Tài khoản | Hướng | Số tiền |
|---|---|---|
| `CASH_VAULT` | DEBIT | amount |
| A | CREDIT | amount |

Nạp 1.000.000: A +1.000.000, `CASH_VAULT` −1.000.000.

### 4.2. Chuyển khoản nội bộ (BANK-045)
`REFERENCE_TYPE = TRANSFER`, `REFERENCE_ID = transferId`. Phí nằm **trong cùng journal** để tiền chuyển và phí thành công hoặc thất bại cùng nhau.

| Tài khoản | Hướng | Số tiền |
|---|---|---|
| A | DEBIT | amount + fee |
| B | CREDIT | amount |
| `FEE_INCOME` | CREDIT | fee (bỏ dòng này khi fee = 0) |

A chuyển 300.000 cho B, phí 2.200: A −302.200, B +300.000, `FEE_INCOME` +2.200.

### 4.3. Thu phí riêng
`REFERENCE_TYPE = FEE`, `REFERENCE_ID` = mã khoản phí. Dùng cho phí không đi kèm một lệnh chuyển (ví dụ phí dịch vụ thu riêng). Phí của lệnh chuyển khoản đi theo journal `TRANSFER` (4.2, 4.4).

| Tài khoản | Hướng | Số tiền |
|---|---|---|
| A | DEBIT | fee |
| `FEE_INCOME` | CREDIT | fee |

### 4.4. Chuyển liên ngân hàng (BANK-052)
`REFERENCE_TYPE = TRANSFER`, `REFERENCE_ID = transferId`. Ghi ngay khi gửi lệnh sang NAPAS; tiền nằm ở `NAPAS_SETTLEMENT` chờ quyết toán.

| Tài khoản | Hướng | Số tiền |
|---|---|---|
| A | DEBIT | amount + fee |
| `NAPAS_SETTLEMENT` | CREDIT | amount |
| `FEE_INCOME` | CREDIT | fee |

NAPAS báo thành công → không ghi thêm bút toán. Quyết toán thực tế với NAPAS nằm ngoài phạm vi hệ thống.

### 4.5. Hoàn tiền / đảo bút toán (BANK-053)
`REFERENCE_TYPE = REVERSAL`, `REFERENCE_ID` = `REFERENCE_ID` của journal gốc (ví dụ `transferId`). `UK_JOURNAL_REFERENCE_ID_TYPE` cho phép cùng `transferId` có một journal `TRANSFER` và một journal `REVERSAL`, và chặn đảo hai lần.

Bút toán đảo = journal gốc với **mọi dòng đổi hướng**, cùng số tiền. Ví dụ đảo 4.4 khi NAPAS báo thất bại:

| Tài khoản | Hướng | Số tiền |
|---|---|---|
| `NAPAS_SETTLEMENT` | DEBIT | amount |
| `FEE_INCOME` | DEBIT | fee |
| A | CREDIT | amount + fee |

Ghi sai thì **không** sửa hay xóa `LEDGER_ENTRY` (trigger chặn), luôn ghi bút toán đảo.

## 5. Ràng buộc trong DB

| Ràng buộc | Nội dung |
|---|---|
| `UK_JOURNAL_REFERENCE_ID_TYPE` | Duy nhất `(REFERENCE_ID, REFERENCE_TYPE)`; `REFERENCE_ID` đứng đầu để tra soát chỉ theo `referenceId` dùng được index |
| `CK_JOURNAL_REFERENCE_TYPE` | `DEPOSIT`, `TRANSFER`, `FEE`, `REVERSAL` |
| `CK_LEDGER_ENTRY_DIRECTION` | `DEBIT`, `CREDIT` |
| `CK_LEDGER_ENTRY_AMOUNT` | `AMOUNT > 0` |
| `FK_LEDGER_ENTRY_JOURNAL_ID`, `FK_LEDGER_ENTRY_ACCOUNT_NO` | Dòng ledger thuộc journal và tài khoản có thật |
| `IX_LEDGER_ENTRY_ACC_CREATED` | `(ACCOUNT_NO, CREATED_AT)` cho sao kê (BANK-033) |
| `IX_LEDGER_ENTRY_JOURNAL_ID` | Lấy các dòng của một journal |
| `TRG_LEDGER_ENTRY_IMMUTABLE` | Trigger mức câu lệnh chặn `UPDATE`/`DELETE` trên `LEDGER_ENTRY` |
| `CK_ACCOUNT_BALANCE` | `ACCOUNT_TYPE = 'GL' OR BALANCE >= 0` |
| `CK_ACCOUNT_CIF` | GL thì `CIF` NULL, tài khoản khách hàng thì `CIF` bắt buộc |

Tổng Nợ = tổng Có **không** ép bằng constraint DB (Oracle không có CHECK nhiều dòng); `PostingService` (BANK-031) kiểm tra trước khi ghi, đối soát cuối ngày (BANK-034) phát hiện nếu lệch.

Không dùng `REVOKE UPDATE, DELETE`: `ACCOUNT_SVC` là chủ bảng nên luôn có mọi quyền trên bảng của mình. Trigger không chặn được `TRUNCATE` (DDL); quyền chạy DDL trên schema chỉ dành cho migration.
