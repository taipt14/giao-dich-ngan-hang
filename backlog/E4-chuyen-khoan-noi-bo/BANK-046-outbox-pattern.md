---
id: BANK-046
epic: E4
title: Outbox pattern cho event nghiệp vụ
priority: High
depends_on: [BANK-045]
status: TODO
---

# BANK-046: Outbox pattern cho event nghiệp vụ

## Bối cảnh nghiệp vụ
Nếu lưu DB thành công nhưng gửi Kafka thất bại (hoặc ngược lại) thì các hệ thống sẽ lệch nhau. Outbox đảm bảo event luôn được gửi nếu và chỉ nếu transaction DB commit.

## Phạm vi kỹ thuật
- `common-core` (hoặc module mới `common-outbox`)
- Áp dụng cho transfer-service, account-service, customer-service

## Yêu cầu chi tiết
1. Bảng `OUTBOX_EVENT`: EVENT_ID, AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, PAYLOAD (CLOB), STATUS (NEW/SENT/FAILED), RETRY_COUNT, CREATED_AT, SENT_AT
2. Ghi outbox **trong cùng transaction** với thay đổi nghiệp vụ
3. Publisher `@Scheduled` mỗi 1 giây: lấy batch NEW bằng `SELECT ... FOR UPDATE SKIP LOCKED`, gửi Kafka, đánh dấu SENT
4. Gửi lỗi quá 10 lần → FAILED, log ERROR
5. Job dọn bản ghi SENT cũ hơn 7 ngày
6. Thay các chỗ đang gửi Kafka trực tiếp (BANK-013, BANK-020, BANK-031) bằng outbox

## Acceptance criteria
- [ ] Transaction rollback → không có event nào được gửi
- [ ] Tắt Kafka, tạo giao dịch, bật lại Kafka → event được gửi
- [ ] Chạy 2 instance publisher → mỗi event chỉ gửi 1 lần ở điều kiện bình thường
- [ ] Phát `TransferCompleted` / `TransferFailed` cho transfer-service

## Ngoài phạm vi
- Debezium CDC (có thể làm sau như phương án thay thế)

## Phụ thuộc
- [BANK-045: Thực hiện chuyển khoản nội bộ](BANK-045-thuc-hien-chuyen-khoan-noi-bo.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
