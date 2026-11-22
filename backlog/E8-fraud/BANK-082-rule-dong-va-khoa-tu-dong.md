---
id: BANK-082
epic: E8
title: Rule fraud cấu hình động và tự động khóa tài khoản
priority: Low
depends_on: [BANK-081, BANK-022]
status: TODO
---

# BANK-082: Rule fraud cấu hình động và tự động khóa tài khoản

## Bối cảnh nghiệp vụ
Vận hành thay đổi ngưỡng rule mà không cần deploy lại. Cảnh báo mức HIGH thì tự động tạm khóa tài khoản.

## Phạm vi kỹ thuật
- fraud-service, account-service

## Yêu cầu chi tiết
1. Bảng `FRAUD_RULE` (mã rule, ngưỡng, cửa sổ thời gian, bật/tắt), cache Redis, làm mới khi có thay đổi
2. API ADMIN CRUD rule
3. account-service consume `fraud.alerts`: mức HIGH → khóa tài khoản (dùng luồng BANK-022) với lý do `FRAUD_AUTO_LOCK`

## Acceptance criteria
- [ ] Đổi ngưỡng velocity từ 5 xuống 3 → có hiệu lực trong vòng 1 phút không cần restart
- [ ] Alert HIGH → tài khoản LOCKED, khách hàng nhận thông báo

## Ngoài phạm vi
- Giao diện quản trị rule

## Phụ thuộc
- [BANK-081: Phát hiện bất thường bằng Kafka Streams (bất đồng bộ)](BANK-081-fraud-kafka-streams.md)
- [BANK-022: Khóa, mở khóa và đóng tài khoản](../E2-account/BANK-022-trang-thai-tai-khoan.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
