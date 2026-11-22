---
id: BANK-001
epic: E0
title: Khởi tạo Maven multi-module và parent POM
priority: High
depends_on: []
status: REVIEW
---

# BANK-001: Khởi tạo Maven multi-module và parent POM

## Bối cảnh nghiệp vụ
Chưa có code. Cần dựng khung repo để các task sau thêm service vào.

## Phạm vi kỹ thuật
- File: `pom.xml` gốc, `common/*/pom.xml`, `services/*/pom.xml`
- Cấu trúc thư mục theo mục 4 của `docs/kien-truc.md`

## Yêu cầu chi tiết
1. Parent POM dùng Java 21, quản lý version Spring Boot 4.1.x và Spring Cloud 2025.1.x bằng `dependencyManagement` (BOM)
2. Tạo module rỗng: `common-core`, `common-events`, `common-test` và 7 service trong `services/`
3. Mỗi service có class `*Application` chạy được, `application.yml` với port theo bảng service
4. Thêm plugin: `maven-surefire`, `maven-failsafe` (integration test có hậu tố `*IT`)

## Acceptance criteria
- [ ] `mvn clean verify` chạy thành công ở thư mục gốc
- [ ] Chạy từng service bằng `mvn -pl services/<tên> spring-boot:run` khởi động được (chưa cần DB)
- [ ] Không có version dependency nào khai báo lặp ở POM con

## Ngoài phạm vi
- Cấu hình Oracle, Kafka, Redis (BANK-002, BANK-003)
- Viết logic nghiệp vụ

## Phụ thuộc
- Không có

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
