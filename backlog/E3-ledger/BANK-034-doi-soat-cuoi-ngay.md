---
id: BANK-034
epic: E3
title: Đối soát cuối ngày bằng PL/SQL
priority: Medium
depends_on: [BANK-031]
status: DONE
---

# BANK-034: Đối soát cuối ngày bằng PL/SQL

## Bối cảnh nghiệp vụ
Cuối mỗi ngày kiểm tra: (1) mọi journal đều cân Nợ = Có; (2) số dư mỗi tài khoản = tổng ledger của tài khoản đó.

## Phạm vi kỹ thuật
- Service `account-service`
- Package PL/SQL `PKG_RECONCILE`
- Bảng `RECONCILE_RESULT`

## Yêu cầu chi tiết
1. Procedure `PKG_RECONCILE.RUN(p_business_date DATE)` ghi kết quả vào `RECONCILE_RESULT` (ngày, loại lỗi, account/journal, giá trị mong đợi, giá trị thực tế)
2. Spring `@Scheduled` gọi procedure lúc 23:55 hằng ngày (cron cấu hình được)
3. API `POST /internal/reconcile/run?date=` cho ADMIN chạy thủ công
4. Có lỗi đối soát → log mức ERROR

## Acceptance criteria
- [ ] Dữ liệu chuẩn → không có dòng lỗi
- [ ] Test cố tình sửa BALANCE bằng SQL → đối soát phát hiện đúng tài khoản lệch
- [ ] Chạy 2 lần cùng ngày → ghi đè kết quả cũ, không nhân đôi

## Ngoài phạm vi
- Đối soát với NAPAS

## Phụ thuộc
- [BANK-031: API posting nội bộ: ghi bút toán và cập nhật số dư](BANK-031-api-posting-noi-bo.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
