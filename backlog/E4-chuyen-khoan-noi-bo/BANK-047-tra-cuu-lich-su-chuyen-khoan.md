---
id: BANK-047
epic: E4
title: Tra cứu lịch sử và chi tiết lệnh chuyển khoản
priority: Low
depends_on: [BANK-045]
status: TODO
---

# BANK-047: Tra cứu lịch sử và chi tiết lệnh chuyển khoản

## Bối cảnh nghiệp vụ
Khách hàng xem danh sách lệnh đã tạo và chi tiết từng lệnh.

## Phạm vi kỹ thuật
- Service `transfer-service`

## Yêu cầu chi tiết
1. `GET /api/v1/transfers?status=&from=&to=&page=&size=`
2. `GET /api/v1/transfers/{id}`: bao gồm lịch sử trạng thái
3. Che một phần số tài khoản đích trong danh sách: `1000****5678`

## Acceptance criteria
- [ ] Chỉ thấy lệnh của chính mình
- [ ] Lọc theo trạng thái và khoảng ngày đúng

## Ngoài phạm vi
- Xuất file

## Phụ thuộc
- [BANK-045: Thực hiện chuyển khoản nội bộ](BANK-045-thuc-hien-chuyen-khoan-noi-bo.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
