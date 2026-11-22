---
id: BANK-061
epic: E6
title: Consumer idempotent dùng chung
priority: High
depends_on: [BANK-060]
status: TODO
---

# BANK-061: Consumer idempotent dùng chung

## Bối cảnh nghiệp vụ
Kafka đảm bảo at-least-once nên một event có thể đến nhiều lần. Khách hàng không được nhận 2 thông báo cho 1 giao dịch.

## Phạm vi kỹ thuật
- `common-core` hoặc `common-events`
- Áp dụng cho mọi consumer

## Yêu cầu chi tiết
1. Annotation hoặc helper `IdempotentConsumer` kiểm tra và ghi `PROCESSED_MESSAGE(EVENT_ID, CONSUMER)` **trong cùng transaction** với xử lý nghiệp vụ
2. Unique key `(EVENT_ID, CONSUMER)`
3. Commit offset thủ công sau khi transaction DB thành công
4. Job dọn `PROCESSED_MESSAGE` cũ hơn 30 ngày

## Acceptance criteria
- [ ] Gửi cùng event 3 lần → chỉ 1 thông báo
- [ ] Xử lý lỗi giữa chừng → không ghi `PROCESSED_MESSAGE`, lần retry xử lý lại được
- [ ] Áp dụng cho toàn bộ consumer đã có (liệt kê trong PR)

## Ngoài phạm vi
- Kafka transactions / exactly-once semantics

## Phụ thuộc
- [BANK-060: Thông báo biến động số dư](BANK-060-thong-bao-bien-dong-so-du.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
