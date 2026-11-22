---
id: BANK-055
epic: E5
title: Resilience4j cho các lời gọi REST giữa service
priority: Medium
depends_on: [BANK-045]
status: TODO
---

# BANK-055: Resilience4j cho các lời gọi REST giữa service

## Bối cảnh nghiệp vụ
Một service chậm hoặc chết không được kéo cả hệ thống chết theo.

## Phạm vi kỹ thuật
- transfer-service, account-service (các REST client)

## Yêu cầu chi tiết
1. Timeout: connect 1 giây, read 3 giây
2. Circuit breaker cho từng service đích; cấu hình trong `application.yml`
3. Retry chỉ áp dụng cho API idempotent (GET, posting có referenceId)
4. Expose trạng thái circuit breaker qua actuator

## Acceptance criteria
- [ ] Tắt account-service → sau ngưỡng lỗi circuit mở, request fail nhanh (< 100ms)
- [ ] Bật lại → circuit chuyển half-open rồi đóng
- [ ] API tạo lệnh (không idempotent ở tầng HTTP) không bị retry tự động

## Ngoài phạm vi
- Bulkhead

## Phụ thuộc
- [BANK-045: Thực hiện chuyển khoản nội bộ](../E4-chuyen-khoan-noi-bo/BANK-045-thuc-hien-chuyen-khoan-noi-bo.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
