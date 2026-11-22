---
id: BANK-006
epic: E0
title: common-test: base class Testcontainers dùng chung
priority: Medium
depends_on: [BANK-002, BANK-003]
status: TODO
---

# BANK-006: common-test: base class Testcontainers dùng chung

## Bối cảnh nghiệp vụ
Integration test cần Oracle, Kafka, Redis thật nhưng không muốn mỗi service cấu hình lại.

## Phạm vi kỹ thuật
- Module `common/common-test`

## Yêu cầu chi tiết
1. Abstract class `AbstractIntegrationTest` khởi động container Oracle Free, Kafka, Redis (dùng singleton container để chạy nhanh)
2. Dùng `@DynamicPropertySource` hoặc `@ServiceConnection` để nạp cấu hình
3. Tiện ích `KafkaTestConsumer` đọc message từ topic với timeout

## Acceptance criteria
- [ ] Một test mẫu trong `account-service` kế thừa base class chạy pass bằng `mvn verify`
- [ ] Chạy test lần 2 trong cùng JVM không khởi động lại container

## Ngoài phạm vi
- Test nghiệp vụ

## Phụ thuộc
- [BANK-002: docker-compose hạ tầng: Oracle, Kafka, Redis](BANK-002-docker-compose-ha-tang.md)
- [BANK-003: Cấu hình datasource Oracle và Flyway cho các service](BANK-003-flyway-ket-noi-oracle.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
