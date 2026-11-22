---
id: BANK-005
epic: E0
title: common-events: event envelope và cấu hình Kafka dùng chung
priority: High
depends_on: [BANK-001]
status: TODO
---

# BANK-005: common-events: event envelope và cấu hình Kafka dùng chung

## Bối cảnh nghiệp vụ
Các service trao đổi event qua Kafka cần một chuẩn chung.

## Phạm vi kỹ thuật
- Module `common/common-events`

## Yêu cầu chi tiết
1. Record `EventEnvelope<T>(eventId, eventType, occurredAt, traceId, source, payload)` theo mục 5 `docs/kien-truc.md`
2. Hằng số tên topic trong class `Topics`
3. Cấu hình JSON serializer/deserializer (Jackson, hỗ trợ `Instant`, `BigDecimal` không mất độ chính xác)
4. Producer mặc định: `acks=all`, `enable.idempotence=true`

## Acceptance criteria
- [ ] Unit test serialize → deserialize envelope có `BigDecimal` 1234567.89 giữ nguyên giá trị
- [ ] Envelope thiếu `eventId` → deserialize báo lỗi rõ ràng

## Ngoài phạm vi
- Định nghĩa payload cụ thể của từng event (làm ở task nghiệp vụ tương ứng)
- Outbox (BANK-046)

## Phụ thuộc
- [BANK-001: Khởi tạo Maven multi-module và parent POM](BANK-001-khoi-tao-maven-multi-module.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
