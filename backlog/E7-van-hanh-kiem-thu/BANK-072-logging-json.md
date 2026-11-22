---
id: BANK-072
epic: E7
title: Chuẩn hóa log dạng JSON
priority: Low
depends_on: [BANK-070, BANK-076]
status: TODO
---

# BANK-072: Chuẩn hóa log dạng JSON

## Bối cảnh nghiệp vụ
Log phải đọc được bằng máy và tra được theo traceId, transferId.

## Phạm vi kỹ thuật
- Tất cả service (cấu hình logback dùng chung)

## Yêu cầu chi tiết
1. Log JSON khi chạy profile `docker`, log dạng text khi chạy local
2. Trường bắt buộc: timestamp, level, service, traceId, spanId, logger, message
3. Đưa `transferId`, `accountNo` (đã che) vào MDC khi xử lý
4. Bộ lọc che dữ liệu nhạy cảm: OTP, CCCD, token

## Acceptance criteria
- [ ] Test: log một chuỗi có CCCD 12 số → output bị che
- [ ] Lọc được log của 1 giao dịch theo traceId

## Ngoài phạm vi
- ELK/Loki

## Phụ thuộc
- [BANK-070: Tracing phân tán qua REST và Kafka](BANK-070-distributed-tracing.md)
- [BANK-076: Đóng gói service thành Docker image và chạy bằng profile docker](BANK-076-docker-image-va-profile.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
