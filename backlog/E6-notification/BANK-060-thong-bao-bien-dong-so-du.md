---
id: BANK-060
epic: E6
title: Thông báo biến động số dư
priority: Medium
depends_on: [BANK-046]
status: TODO
---

# BANK-060: Thông báo biến động số dư

## Bối cảnh nghiệp vụ
Mỗi khi số dư thay đổi, khách hàng nhận thông báo: `TK 1000****5678 -300,000 VND lúc 10:15 21/11. SD: 700,000 VND. ND: ...`

## Phạm vi kỹ thuật
- Service `notification-service`
- Bảng `NOTIFICATION`

## Yêu cầu chi tiết
1. Consume `account.events` (`BalanceChanged`)
2. Định dạng nội dung theo mẫu trên, số tiền có dấu phân cách hàng nghìn
3. Kênh gửi qua interface `NotificationSender`; bản cài đặt dev: log + lưu DB
4. Lưu bảng `NOTIFICATION`: ID, CIF, CHANNEL, CONTENT, STATUS, EVENT_ID, CREATED_AT
5. API `GET /api/v1/notifications` cho khách hàng xem lại

## Acceptance criteria
- [ ] Chuyển khoản thành công → cả bên gửi và bên nhận có thông báo
- [ ] Unit test định dạng nội dung (số âm, số dương, số lớn)

## Ngoài phạm vi
- Gửi email/SMS/push thật

## Phụ thuộc
- [BANK-046: Outbox pattern cho event nghiệp vụ](../E4-chuyen-khoan-noi-bo/BANK-046-outbox-pattern.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
