---
id: BANK-050
epic: E5
title: mock-napas-service: giả lập ngân hàng đối tác
priority: High
depends_on: [BANK-005, BANK-003]
status: TODO
---

# BANK-050: mock-napas-service: giả lập ngân hàng đối tác

## Bối cảnh nghiệp vụ
Giả lập NAPAS và các ngân hàng khác để test luồng liên ngân hàng, bao gồm cả trường hợp lỗi và chậm.

## Phạm vi kỹ thuật
- Service `mock-napas-service`
- Topic `napas.requests`, `napas.responses`

## Yêu cầu chi tiết
1. Danh sách ngân hàng và tài khoản giả lập lưu trong bảng (mã ngân hàng, số tài khoản, tên chủ tài khoản)
2. Consume `InterbankTransferRequested`, trả `InterbankTransferResult` với kết quả SUCCESS/FAILED
3. Kịch bản cấu hình được theo số tài khoản đích: đuôi `0001` → luôn thất bại; `0002` → trả lời chậm 2 phút; `0003` → không bao giờ trả lời; còn lại → thành công
4. API `GET /napas/transfers/{transferId}` để tra soát trạng thái
5. Idempotent theo transferId

## Acceptance criteria
- [ ] Mỗi kịch bản có integration test
- [ ] Nhận cùng request 2 lần → chỉ xử lý 1 lần, trả cùng kết quả

## Ngoài phạm vi
- Giao thức ISO 8583 thật

## Phụ thuộc
- [BANK-005: common-events: event envelope và cấu hình Kafka dùng chung](../E0-nen-tang/BANK-005-common-events-envelope.md)
- [BANK-003: Cấu hình datasource Oracle và Flyway cho các service](../E0-nen-tang/BANK-003-flyway-ket-noi-oracle.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
