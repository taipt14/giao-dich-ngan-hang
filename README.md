# Giao dịch ngân hàng — Backlog

Project mô phỏng nghiệp vụ chuyển khoản ngân hàng: Java 21, Spring Boot microservices, Kafka, Redis, Oracle.

## Tài liệu

- [Kiến trúc và quy ước bắt buộc](docs/kien-truc.md): context chính cho AI agent
- [Mẫu task](docs/mau-task.md)
- [Quy tắc viết task cho AI](docs/quy-tac-viet-task.md)

## Thứ tự gợi ý

E0 → E1 → E2 (BANK-020, 021, 022) → E3 → BANK-023 → E4 → E5 → E6 → E7 → E8

Một task chỉ được bắt đầu khi mọi task trong `depends_on` đã `DONE`.

## E0. Nền tảng

Khung repo, hạ tầng local, thư viện dùng chung, CI

| ID | Task | Ưu tiên | Phụ thuộc |
|---|---|---|---|
| BANK-001 | [Khởi tạo Maven multi-module và parent POM](backlog/E0-nen-tang/BANK-001-khoi-tao-maven-multi-module.md) | High | — |
| BANK-002 | [docker-compose hạ tầng: Oracle, Kafka, Redis](backlog/E0-nen-tang/BANK-002-docker-compose-ha-tang.md) | High | BANK-001 |
| BANK-003 | [Cấu hình datasource Oracle và Flyway cho các service](backlog/E0-nen-tang/BANK-003-flyway-ket-noi-oracle.md) | High | BANK-002 |
| BANK-004 | [common-core: định dạng lỗi chuẩn và mã lỗi](backlog/E0-nen-tang/BANK-004-common-core-xu-ly-loi.md) | High | BANK-001 |
| BANK-005 | [common-events: event envelope và cấu hình Kafka dùng chung](backlog/E0-nen-tang/BANK-005-common-events-envelope.md) | High | BANK-001 |
| BANK-006 | [common-test: base class Testcontainers dùng chung](backlog/E0-nen-tang/BANK-006-common-test-testcontainers.md) | Medium | BANK-002, BANK-003 |
| BANK-007 | [CI: GitHub Actions build và test](backlog/E0-nen-tang/BANK-007-ci-github-actions.md) | Low | BANK-006 |

## E1. Auth & Customer

Xác thực, gateway, hồ sơ khách hàng

| ID | Task | Ưu tiên | Phụ thuộc |
|---|---|---|---|
| BANK-010 | [Keycloak: realm, client và role](backlog/E1-auth-customer/BANK-010-keycloak-realm.md) | High | BANK-002 |
| BANK-011 | [API Gateway: routing và xác thực JWT](backlog/E1-auth-customer/BANK-011-api-gateway-routing-jwt.md) | High | BANK-010 |
| BANK-012 | [Rate limit tại gateway bằng Redis](backlog/E1-auth-customer/BANK-012-gateway-rate-limit-redis.md) | Medium | BANK-011 |
| BANK-013 | [customer-service: đăng ký khách hàng (CIF)](backlog/E1-auth-customer/BANK-013-dang-ky-khach-hang.md) | High | BANK-003, BANK-004, BANK-005 |
| BANK-014 | [customer-service: tra cứu, cập nhật và đổi trạng thái khách hàng](backlog/E1-auth-customer/BANK-014-tra-cuu-cap-nhat-khach-hang.md) | Medium | BANK-013 |

## E2. Account

Tài khoản thanh toán, số dư, trạng thái

| ID | Task | Ưu tiên | Phụ thuộc |
|---|---|---|---|
| BANK-020 | [account-service: mở tài khoản thanh toán](backlog/E2-account/BANK-020-mo-tai-khoan-thanh-toan.md) | High | BANK-014 |
| BANK-021 | [Truy vấn số dư và danh sách tài khoản (cache Redis)](backlog/E2-account/BANK-021-truy-van-so-du-cache-redis.md) | High | BANK-020 |
| BANK-022 | [Khóa, mở khóa và đóng tài khoản](backlog/E2-account/BANK-022-trang-thai-tai-khoan.md) | Medium | BANK-020 |
| BANK-023 | [Nạp tiền tại quầy (ADMIN)](backlog/E2-account/BANK-023-nap-tien-tai-quay.md) | High | BANK-031 |

## E3. Ledger

Sổ cái bút toán kép trong account-service

| ID | Task | Ưu tiên | Phụ thuộc |
|---|---|---|---|
| BANK-030 | [Thiết kế bảng sổ cái và tài khoản nội bộ (GL)](backlog/E3-ledger/BANK-030-thiet-ke-bang-ledger.md) | High | BANK-020 |
| BANK-031 | [API posting nội bộ: ghi bút toán và cập nhật số dư](backlog/E3-ledger/BANK-031-api-posting-noi-bo.md) | High | BANK-030 |
| BANK-032 | [Trừ số dư an toàn khi có nhiều giao dịch đồng thời](backlog/E3-ledger/BANK-032-tru-so-du-an-toan-concurrency.md) | High | BANK-031 |
| BANK-033 | [Sao kê tài khoản](backlog/E3-ledger/BANK-033-sao-ke-tai-khoan.md) | Medium | BANK-031 |
| BANK-034 | [Đối soát cuối ngày bằng PL/SQL](backlog/E3-ledger/BANK-034-doi-soat-cuoi-ngay.md) | Medium | BANK-031 |

## E4. Chuyển khoản nội bộ

Lệnh chuyển khoản, OTP, hạn mức, phí, outbox

| ID | Task | Ưu tiên | Phụ thuộc |
|---|---|---|---|
| BANK-040 | [transfer-service: bảng TRANSFER và máy trạng thái](backlog/E4-chuyen-khoan-noi-bo/BANK-040-bang-transfer-state-machine.md) | High | BANK-003, BANK-004 |
| BANK-041 | [API khởi tạo chuyển khoản với Idempotency-Key](backlog/E4-chuyen-khoan-noi-bo/BANK-041-api-khoi-tao-chuyen-khoan-idempotency.md) | High | BANK-040 |
| BANK-042 | [Xác thực OTP bằng Redis](backlog/E4-chuyen-khoan-noi-bo/BANK-042-otp-redis.md) | High | BANK-041 |
| BANK-043 | [Hạn mức giao dịch theo lần và theo ngày](backlog/E4-chuyen-khoan-noi-bo/BANK-043-han-muc-giao-dich.md) | High | BANK-041 |
| BANK-044 | [Tính phí giao dịch](backlog/E4-chuyen-khoan-noi-bo/BANK-044-tinh-phi-giao-dich.md) | Medium | BANK-040 |
| BANK-045 | [Thực hiện chuyển khoản nội bộ](backlog/E4-chuyen-khoan-noi-bo/BANK-045-thuc-hien-chuyen-khoan-noi-bo.md) | High | BANK-032, BANK-042, BANK-043, BANK-044 |
| BANK-046 | [Outbox pattern cho event nghiệp vụ](backlog/E4-chuyen-khoan-noi-bo/BANK-046-outbox-pattern.md) | High | BANK-045 |
| BANK-047 | [Tra cứu lịch sử và chi tiết lệnh chuyển khoản](backlog/E4-chuyen-khoan-noi-bo/BANK-047-tra-cuu-lich-su-chuyen-khoan.md) | Low | BANK-045 |

## E5. Chuyển khoản liên ngân hàng

Mock NAPAS, saga, bù trừ, tra soát

| ID | Task | Ưu tiên | Phụ thuộc |
|---|---|---|---|
| BANK-050 | [mock-napas-service: giả lập ngân hàng đối tác](backlog/E5-lien-ngan-hang/BANK-050-mock-napas-service.md) | High | BANK-005, BANK-003 |
| BANK-051 | [Truy vấn tên người nhận liên ngân hàng](backlog/E5-lien-ngan-hang/BANK-051-truy-van-ten-nguoi-nhan.md) | Medium | BANK-050, BANK-055 |
| BANK-052 | [Saga chuyển liên ngân hàng: trừ tiền và gửi NAPAS](backlog/E5-lien-ngan-hang/BANK-052-saga-chuyen-lien-ngan-hang.md) | High | BANK-045, BANK-046, BANK-050 |
| BANK-053 | [Xử lý phản hồi NAPAS và hoàn tiền (bù trừ)](backlog/E5-lien-ngan-hang/BANK-053-xu-ly-phan-hoi-napas-bu-tru.md) | High | BANK-052 |
| BANK-054 | [Timeout và tra soát giao dịch treo](backlog/E5-lien-ngan-hang/BANK-054-timeout-tra-soat.md) | High | BANK-053 |
| BANK-055 | [Resilience4j cho các lời gọi REST giữa service](backlog/E5-lien-ngan-hang/BANK-055-resilience4j.md) | Medium | BANK-045 |

## E6. Notification

Thông báo biến động số dư, DLQ

| ID | Task | Ưu tiên | Phụ thuộc |
|---|---|---|---|
| BANK-060 | [Thông báo biến động số dư](backlog/E6-notification/BANK-060-thong-bao-bien-dong-so-du.md) | Medium | BANK-046 |
| BANK-061 | [Consumer idempotent dùng chung](backlog/E6-notification/BANK-061-consumer-idempotent.md) | High | BANK-060 |
| BANK-062 | [Retry, DLQ và replay message lỗi](backlog/E6-notification/BANK-062-retry-dlq-replay.md) | Medium | BANK-061 |

## E7. Vận hành & kiểm thử

Tracing, metrics, logging, test tải, E2E

| ID | Task | Ưu tiên | Phụ thuộc |
|---|---|---|---|
| BANK-070 | [Tracing phân tán qua REST và Kafka](backlog/E7-van-hanh-kiem-thu/BANK-070-distributed-tracing.md) | Medium | BANK-046 |
| BANK-071 | [Metrics Prometheus và dashboard Grafana](backlog/E7-van-hanh-kiem-thu/BANK-071-metrics-prometheus-grafana.md) | Low | BANK-070 |
| BANK-072 | [Chuẩn hóa log dạng JSON](backlog/E7-van-hanh-kiem-thu/BANK-072-logging-json.md) | Low | BANK-070 |
| BANK-073 | [Test tải và test đồng thời cho chuyển khoản](backlog/E7-van-hanh-kiem-thu/BANK-073-load-test-chuyen-khoan.md) | Medium | BANK-045 |
| BANK-074 | [Test end-to-end toàn hệ thống](backlog/E7-van-hanh-kiem-thu/BANK-074-test-e2e-toan-he-thong.md) | Medium | BANK-054, BANK-062 |
| BANK-075 | [README và tài liệu dự án](backlog/E7-van-hanh-kiem-thu/BANK-075-readme-tai-lieu-du-an.md) | Low | BANK-074 |

## E8. Fraud (mở rộng)

Phát hiện gian lận đồng bộ và bất đồng bộ

| ID | Task | Ưu tiên | Phụ thuộc |
|---|---|---|---|
| BANK-080 | [fraud-service: kiểm tra tần suất giao dịch (đồng bộ)](backlog/E8-fraud/BANK-080-fraud-velocity-check.md) | Low | BANK-045 |
| BANK-081 | [Phát hiện bất thường bằng Kafka Streams (bất đồng bộ)](backlog/E8-fraud/BANK-081-fraud-kafka-streams.md) | Low | BANK-080 |
| BANK-082 | [Rule fraud cấu hình động và tự động khóa tài khoản](backlog/E8-fraud/BANK-082-rule-dong-va-khoa-tu-dong.md) | Low | BANK-081, BANK-022 |

**Tổng cộng:** 47 task.
