# Kiến trúc hệ thống giao dịch ngân hàng

> File này là **context chính** cho AI agent. Agent phải đọc file này trước khi làm bất kỳ task nào.

## 1. Mục tiêu

Hệ thống mô phỏng nghiệp vụ chuyển khoản của một ngân hàng nhỏ:
- Khách hàng, tài khoản thanh toán, số dư
- Sổ cái bút toán kép (double-entry ledger)
- Chuyển khoản nội bộ và liên ngân hàng (giả lập NAPAS)
- Thông báo biến động số dư
- Phát hiện gian lận (mở rộng)

## 2. Công nghệ

| Thành phần | Lựa chọn |
|---|---|
| Ngôn ngữ | Java 21 |
| Framework | Spring Boot 4.1.x, Spring Cloud 2025.1.x (Gateway, Circuit Breaker) |
| Build | Maven multi-module |
| Database | Oracle Database Free (image `gvenzl/oracle-free`) |
| Migration | Flyway |
| Messaging | Apache Kafka (KRaft, không dùng Zookeeper) |
| Cache / Lock | Redis 7 |
| Auth | Keycloak (OAuth2 / JWT) |
| Resilience | Resilience4j |
| Observability | Micrometer Tracing, Zipkin, Prometheus, Grafana |
| Test | JUnit 5, Testcontainers, AssertJ |

## 3. Các service

| Service | Port | Schema Oracle | Trách nhiệm |
|---|---|---|---|
| api-gateway | 8080 | — | Routing, xác thực JWT, rate limit |
| customer-service | 8081 | `CUSTOMER_SVC` | Hồ sơ khách hàng (CIF) |
| account-service | 8082 | `ACCOUNT_SVC` | Tài khoản, số dư, **sổ cái (ledger)** |
| transfer-service | 8084 | `TRANSFER_SVC` | Lệnh chuyển khoản, OTP, hạn mức, phí, saga |
| mock-napas-service | 8085 | `NAPAS_SVC` | Giả lập ngân hàng đối tác / NAPAS |
| notification-service | 8086 | `NOTI_SVC` | Thông báo biến động số dư |
| fraud-service | 8087 | `FRAUD_SVC` | Phát hiện gian lận |

**Quyết định quan trọng:** ledger nằm trong `account-service` để việc thay đổi số dư và ghi bút toán diễn ra trong **cùng một transaction Oracle**. Không tách ledger thành service riêng.

## 4. Cấu trúc repo

```
giao-dich-ngan-hang/
├── pom.xml                  # parent POM
├── common/
│   ├── common-core/         # error response, exception, utils
│   ├── common-events/       # event envelope, các class event
│   └── common-test/         # base class Testcontainers
├── services/
│   ├── api-gateway/
│   ├── customer-service/
│   ├── account-service/
│   ├── transfer-service/
│   ├── mock-napas-service/
│   ├── notification-service/
│   └── fraud-service/
├── infra/
│   └── docker-compose.yml
├── docs/
└── backlog/
```

Package trong mỗi service:
```
vn.bank.<service>/
├── api/           # controller, request/response DTO
├── application/   # service, use case
├── domain/        # entity, enum, quy tắc nghiệp vụ
├── infrastructure/
│   ├── persistence/   # repository
│   ├── messaging/     # producer, consumer
│   └── client/        # REST client gọi service khác
└── config/
```

## 5. Kafka topic

| Topic | Producer | Consumer | Event |
|---|---|---|---|
| `customer.events` | customer-service | account-service | `CustomerCreated`, `CustomerStatusChanged` |
| `account.events` | account-service | notification-service | `AccountOpened`, `BalanceChanged`, `AccountStatusChanged` |
| `transfer.events` | transfer-service | notification-service, fraud-service | `TransferCompleted`, `TransferFailed` |
| `napas.requests` | transfer-service | mock-napas-service | `InterbankTransferRequested` |
| `napas.responses` | mock-napas-service | transfer-service | `InterbankTransferResult` |
| `fraud.alerts` | fraud-service | account-service, notification-service | `FraudAlertRaised` |
| `<topic>.DLQ` | consumer lỗi | admin | message lỗi sau khi hết retry |

Mọi event bọc trong envelope chung:
```json
{
  "eventId": "uuid",
  "eventType": "TransferCompleted",
  "occurredAt": "2026-11-21T10:00:00Z",
  "traceId": "...",
  "source": "transfer-service",
  "payload": { }
}
```
Key của message = id của aggregate (ví dụ `transferId`, `accountNo`) để đảm bảo thứ tự theo aggregate.

## 6. Redis key

| Key | Mục đích | TTL |
|---|---|---|
| `idem:{clientId}:{idempotencyKey}` | Chống xử lý trùng request | 24h |
| `otp:{transferId}` | Mã OTP (lưu dạng hash) | 3 phút |
| `otp:fail:{transferId}` | Đếm số lần nhập sai OTP | 3 phút |
| `limit:{accountNo}:{yyyyMMdd}` | Tổng tiền đã chuyển trong ngày | hết ngày |
| `balance:{accountNo}` | Cache số dư (chỉ để đọc) | 5 phút |
| `lock:account:{accountNo}` | Distributed lock (nếu cần) | 10 giây |
| `fraud:velocity:{accountNo}` | Sorted set đếm giao dịch theo thời gian | 1 giờ |

## 7. Quy ước viết code

Toàn bộ quy ước viết code (tiền tệ, DB, Kafka, API, logging, test, git...) nằm trong
[convention-code.md](convention-code.md). Agent **bắt buộc** đọc file đó trước khi code.

## 8. Lệnh thường dùng

```bash
# Khởi động hạ tầng
docker compose -f infra/docker-compose.yml up -d

# Build và chạy toàn bộ test
mvn clean verify

# Chạy một service
mvn -pl services/account-service spring-boot:run
```
