---
id: BANK-071
epic: E7
title: Metrics Prometheus và dashboard Grafana
priority: Low
depends_on: [BANK-070]
status: TODO
---

# BANK-071: Metrics Prometheus và dashboard Grafana

## Bối cảnh nghiệp vụ
Theo dõi sức khỏe hệ thống và số liệu nghiệp vụ.

## Phạm vi kỹ thuật
- Tất cả service
- `infra/prometheus.yml`, `infra/grafana/`

## Yêu cầu chi tiết
1. Expose `/actuator/prometheus`
2. Metric nghiệp vụ: số lệnh theo trạng thái, tổng tiền chuyển, thời gian xử lý lệnh, số lệnh UNKNOWN, độ trễ consumer (consumer lag), số message outbox chưa gửi
3. Dashboard Grafana provision sẵn (file JSON)

## Acceptance criteria
- [ ] Mở Grafana thấy dashboard có dữ liệu sau khi chạy vài giao dịch
- [ ] Metric không chứa số tài khoản hoặc CIF trong label (tránh high cardinality và lộ dữ liệu)

## Ngoài phạm vi
- Cảnh báo (alerting)

## Phụ thuộc
- [BANK-070: Tracing phân tán qua REST và Kafka](BANK-070-distributed-tracing.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
