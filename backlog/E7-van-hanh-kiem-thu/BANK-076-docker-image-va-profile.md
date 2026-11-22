---
id: BANK-076
epic: E7
title: Đóng gói service thành Docker image và chạy bằng profile docker
priority: Medium
depends_on: [BANK-054, BANK-062]
status: TODO
---

# BANK-076: Đóng gói service thành Docker image và chạy bằng profile docker

## Bối cảnh nghiệp vụ
Hiện các service chỉ chạy trên máy bằng `spring-boot:run`, trỏ tới hạ tầng qua `localhost`. BANK-072 (log JSON ở profile `docker`) và BANK-074 (test end-to-end chạy cả hệ thống bằng compose) đều cần service chạy trong container. Convention mục 16 định nghĩa profile `local`, `docker`, `test`, nhưng chưa task nào tạo profile `docker`.

## Phạm vi kỹ thuật
- `services/*/Dockerfile` (7 service)
- `services/*/src/main/resources/application-docker.yml`
- `infra/docker-compose.yml`: thêm 7 service trong compose profile `apps`
- `infra/.env.example`, `infra/README.md`

## Yêu cầu chi tiết
1. Dockerfile multi-stage cho từng service, dựa trên jar đã build bằng `./mvnw package`:
   - Image chạy là `eclipse-temurin:21-jre`, tách layer của Spring Boot (`java -Djarmode=tools ... extract --layers`) để lần build sau nhanh hơn.
   - Chạy bằng user không phải root.
2. Profile `local` là cấu hình hiện có trong `application.yml` (trỏ `localhost`). Profile `docker` (`application-docker.yml`) chỉ ghi đè địa chỉ hạ tầng:

   | Thành phần | Giá trị trong profile `docker` |
   |---|---|
   | Oracle | `jdbc:oracle:thin:@oracle:1521/FREEPDB1` |
   | Kafka | `kafka:29092` |
   | Redis | `redis:6379` |
   | Keycloak | host `keycloak` |
   | Service khác | gọi theo tên service trong compose |
3. Trong `infra/docker-compose.yml`, 7 service nằm trong compose profile `apps`:
   - Đặt `SPRING_PROFILES_ACTIVE=docker`.
   - `depends_on` hạ tầng với `condition: service_healthy`.
   - Healthcheck gọi `/actuator/health`.
   - Port host giữ đúng bảng service ở mục 3 `kien-truc.md`.
   - Lý do để trong profile `apps`: `docker compose up -d` không kèm profile vẫn chỉ khởi hạ tầng như BANK-002. Developer chạy service trên máy để debug không bị trùng port.
4. Mật khẩu và secret đọc từ `infra/.env`, bổ sung biến mới vào `infra/.env.example`.

## Acceptance criteria
- [ ] `docker compose -f infra/docker-compose.yml up -d --wait` (không profile) → chỉ có container hạ tầng, giống BANK-002
- [ ] `docker compose -f infra/docker-compose.yml --profile apps up -d --build --wait` → 7 container service ở trạng thái healthy
- [ ] `curl localhost:8082/actuator/health` → `UP`, có component `db` `UP` (account-service kết nối Oracle qua `oracle:1521`)
- [ ] Lấy token `customer01` (BANK-010), gọi API qua gateway `localhost:8080` → request tới đúng service trong compose
- [ ] `docker compose exec account-service id -u` → khác `0`
- [ ] `infra/README.md` có lệnh khởi động và dừng toàn bộ hệ thống

## Ngoài phạm vi
- Log JSON (BANK-072)
- Push image lên registry, build image trong CI
- Kubernetes, Helm

## Phụ thuộc
- [BANK-054: Timeout và tra soát giao dịch treo](../E5-lien-ngan-hang/BANK-054-timeout-tra-soat.md)
- [BANK-062: Retry, DLQ và replay message lỗi](../E6-notification/BANK-062-retry-dlq-replay.md)

Hai task trên là những task cuối cùng thêm cấu hình hạ tầng cho service. Làm sau chúng để profile `docker` phủ đủ mọi kết nối.

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md), mục 16
