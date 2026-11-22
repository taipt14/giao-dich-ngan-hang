---
id: BANK-003
epic: E0
title: Cấu hình datasource Oracle và Flyway cho các service
priority: High
depends_on: [BANK-002]
status: TODO
---

# BANK-003: Cấu hình datasource Oracle và Flyway cho các service

## Bối cảnh nghiệp vụ
Mỗi service cần kết nối schema riêng và quản lý thay đổi DB bằng migration.

## Phạm vi kỹ thuật
- `application.yml` của các service có DB
- `src/main/resources/db/migration/`

## Yêu cầu chi tiết
1. Thêm `spring-boot-starter-data-jpa`, `ojdbc11`, `flyway-database-oracle`
2. Mỗi service trỏ tới schema của chính nó
3. Tạo migration `V1__baseline.sql` gồm bảng `PROCESSED_MESSAGE` (EVENT_ID, CONSUMER, PROCESSED_AT) dùng chung cho consumer idempotent
4. Cấu hình HikariCP hợp lý (pool size, timeout)

## Acceptance criteria
- [ ] Khởi động service → Flyway chạy migration, bảng `flyway_schema_history` được tạo đúng schema
- [ ] Không service nào dùng user/schema của service khác

## Ngoài phạm vi
- Bảng nghiệp vụ (các epic sau)

## Phụ thuộc
- [BANK-002: docker-compose hạ tầng: Oracle, Kafka, Redis](BANK-002-docker-compose-ha-tang.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
