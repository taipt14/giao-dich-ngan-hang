---
id: BANK-030
epic: E3
title: Thiết kế bảng sổ cái và tài khoản nội bộ (GL)
priority: High
depends_on: [BANK-020]
status: TODO
---

# BANK-030: Thiết kế bảng sổ cái và tài khoản nội bộ (GL)

## Bối cảnh nghiệp vụ
Mọi thay đổi số dư phải được ghi bằng bút toán kép: mỗi giao dịch có ít nhất 1 dòng Nợ và 1 dòng Có, tổng Nợ = tổng Có. Tiền vào/ra ngân hàng đi qua các tài khoản nội bộ.

## Phạm vi kỹ thuật
- Service `account-service`
- Migration Flyway mới

## Yêu cầu chi tiết
1. Bảng `JOURNAL`: JOURNAL_ID, REFERENCE_TYPE (DEPOSIT, TRANSFER, FEE, REVERSAL...), REFERENCE_ID, DESCRIPTION, CREATED_AT. Unique `(REFERENCE_TYPE, REFERENCE_ID)`
2. Bảng `LEDGER_ENTRY`: ENTRY_ID, JOURNAL_ID, ACCOUNT_NO, DIRECTION (DEBIT/CREDIT), AMOUNT > 0, BALANCE_AFTER, CREATED_AT
3. Tài khoản nội bộ tạo sẵn bằng migration (lưu trong bảng ACCOUNT với ACCOUNT_TYPE = `GL`): `CASH_VAULT`, `FEE_INCOME`, `NAPAS_SETTLEMENT`
4. Tài khoản GL được phép âm số dư (ràng buộc CHECK chỉ áp dụng cho tài khoản khách hàng)
5. Index phục vụ sao kê: `(ACCOUNT_NO, CREATED_AT)`
6. Thu hồi quyền UPDATE, DELETE trên `LEDGER_ENTRY` (hoặc dùng trigger chặn)

## Acceptance criteria
- [ ] Migration chạy thành công trên DB trống
- [ ] UPDATE hoặc DELETE vào `LEDGER_ENTRY` → Oracle báo lỗi
- [ ] Có file `docs/ledger.md` giải thích bút toán của từng loại giao dịch (nạp tiền, chuyển nội bộ, phí, liên ngân hàng, hoàn tiền)

## Ngoài phạm vi
- API (BANK-031)

## Phụ thuộc
- [BANK-020: account-service: mở tài khoản thanh toán](../E2-account/BANK-020-mo-tai-khoan-thanh-toan.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
