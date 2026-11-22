---
id: BANK-070
epic: E7
title: Tracing phân tán qua REST và Kafka
priority: Medium
depends_on: [BANK-046]
status: TODO
---

# BANK-070: Tracing phân tán qua REST và Kafka

## Bối cảnh nghiệp vụ
Khi một giao dịch lỗi cần lần theo được toàn bộ hành trình qua các service.

## Phạm vi kỹ thuật
- Tất cả service
- `infra/docker-compose.yml` (thêm Zipkin)

## Yêu cầu chi tiết
1. Micrometer Tracing + exporter Zipkin (hoặc OpenTelemetry)
2. Truyền trace context qua header Kafka; với outbox: lưu traceId vào bảng outbox rồi gắn lại khi gửi
3. `traceId` xuất hiện trong response lỗi (BANK-004) và trong log

## Acceptance criteria
- [ ] Một lệnh liên ngân hàng hiển thị trên Zipkin thành 1 trace đi qua gateway → transfer → account → napas → transfer → notification
- [ ] Ảnh chụp trace đính kèm vào `docs/observability.md`

## Ngoài phạm vi
- Lưu trace lâu dài

## Phụ thuộc
- [BANK-046: Outbox pattern cho event nghiệp vụ](../E4-chuyen-khoan-noi-bo/BANK-046-outbox-pattern.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
