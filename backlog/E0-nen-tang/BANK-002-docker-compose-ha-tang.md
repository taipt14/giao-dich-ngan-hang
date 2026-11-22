---
id: BANK-002
epic: E0
title: docker-compose hạ tầng: Oracle, Kafka, Redis
priority: High
depends_on: [BANK-001]
status: TODO
---

# BANK-002: docker-compose hạ tầng: Oracle, Kafka, Redis

## Bối cảnh nghiệp vụ
Developer cần chạy toàn bộ hạ tầng trên máy local bằng 1 lệnh.

## Phạm vi kỹ thuật
- File: `infra/docker-compose.yml`, `infra/oracle/init/*.sql`, `infra/README.md`

## Yêu cầu chi tiết
1. Oracle: image `gvenzl/oracle-free`, port 1521, có healthcheck
2. Kafka: chế độ KRaft (1 broker), port 9092
3. Kafka UI (ví dụ `provectuslabs/kafka-ui`) port 8090 để xem topic
4. Redis 7, port 6379
5. Script init Oracle tạo user/schema: `CUSTOMER_SVC`, `ACCOUNT_SVC`, `TRANSFER_SVC`, `NAPAS_SVC`, `NOTI_SVC`, `FRAUD_SVC` (mật khẩu đọc từ biến môi trường)
6. File `.env.example` chứa các biến môi trường

## Acceptance criteria
- [ ] `docker compose -f infra/docker-compose.yml up -d` chạy, mọi container ở trạng thái healthy
- [ ] Kết nối được Oracle bằng từng user schema
- [ ] Mở Kafka UI thấy broker
- [ ] `infra/README.md` hướng dẫn khởi động và reset dữ liệu

## Ngoài phạm vi
- Keycloak (BANK-010)
- Zipkin, Prometheus, Grafana (E7)
- Tạo bảng nghiệp vụ

## Phụ thuộc
- [BANK-001: Khởi tạo Maven multi-module và parent POM](BANK-001-khoi-tao-maven-multi-module.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
