---
id: BANK-062
epic: E6
title: Retry, DLQ và replay message lỗi
priority: Medium
depends_on: [BANK-061]
status: TODO
---

# BANK-062: Retry, DLQ và replay message lỗi

## Bối cảnh nghiệp vụ
Message lỗi không được làm tắc cả partition, nhưng cũng không được mất.

## Phạm vi kỹ thuật
- Cấu hình Kafka consumer dùng chung
- notification-service (áp dụng đầu tiên)

## Yêu cầu chi tiết
1. `DefaultErrorHandler` retry 3 lần, backoff 1s, 2s, 4s
2. Hết retry → `DeadLetterPublishingRecoverer` gửi sang `<topic>.DLQ` kèm header lý do lỗi
3. Lỗi không thể retry (deserialize, validate) → vào DLQ ngay
4. API ADMIN: `GET /internal/dlq/{topic}` xem message, `POST /internal/dlq/{topic}/replay` gửi lại topic gốc

## Acceptance criteria
- [ ] Message lỗi → sau 3 lần vào DLQ, message tiếp theo trong partition vẫn được xử lý
- [ ] Replay sau khi sửa lỗi → xử lý thành công, không trùng (nhờ BANK-061)

## Ngoài phạm vi
- Giao diện quản trị

## Phụ thuộc
- [BANK-061: Consumer idempotent dùng chung](BANK-061-consumer-idempotent.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
