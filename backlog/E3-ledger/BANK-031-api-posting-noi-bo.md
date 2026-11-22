---
id: BANK-031
epic: E3
title: API posting nội bộ: ghi bút toán và cập nhật số dư
priority: High
depends_on: [BANK-030]
status: TODO
---

# BANK-031: API posting nội bộ: ghi bút toán và cập nhật số dư

## Bối cảnh nghiệp vụ
Đây là điểm duy nhất được phép thay đổi số dư. Các service khác (transfer-service) gọi API này.

## Phạm vi kỹ thuật
- Service `account-service`
- Lớp `PostingService`

## Yêu cầu chi tiết
1. API nội bộ `POST /internal/postings`. Body: `referenceType`, `referenceId`, danh sách `lines[] {accountNo, direction, amount}`
2. Kiểm tra: tổng DEBIT = tổng CREDIT, mọi amount > 0, tài khoản khách hàng đều ACTIVE
3. Trong **một transaction**: tạo JOURNAL, khóa các tài khoản liên quan, cập nhật số dư, ghi LEDGER_ENTRY kèm BALANCE_AFTER
4. Idempotent theo `(referenceType, referenceId)`: đã tồn tại → trả lại journal cũ, không ghi thêm
5. Phát `BalanceChanged` cho từng tài khoản khách hàng bị thay đổi

## Acceptance criteria
- [ ] Bút toán không cân → 400 `JOURNAL_NOT_BALANCED`, không có dữ liệu nào được ghi
- [ ] Một dòng lỗi (tài khoản LOCKED) → toàn bộ rollback
- [ ] Gọi 2 lần cùng referenceId → chỉ 1 journal
- [ ] Sau posting, `BALANCE` của tài khoản = `BALANCE_AFTER` của dòng ledger cuối

## Ngoài phạm vi
- Xử lý concurrency tối ưu (BANK-032)
- API public cho client

## Phụ thuộc
- [BANK-030: Thiết kế bảng sổ cái và tài khoản nội bộ (GL)](BANK-030-thiet-ke-bang-ledger.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
