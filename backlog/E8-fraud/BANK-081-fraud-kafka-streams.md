---
id: BANK-081
epic: E8
title: Phát hiện bất thường bằng Kafka Streams (bất đồng bộ)
priority: Low
depends_on: [BANK-080]
status: TODO
---

# BANK-081: Phát hiện bất thường bằng Kafka Streams (bất đồng bộ)

## Bối cảnh nghiệp vụ
Phát hiện mẫu hành vi sau giao dịch, ví dụ một tài khoản nhận tiền từ hơn 10 tài khoản khác nhau trong 10 phút (dấu hiệu tài khoản trung gian lừa đảo).

## Phạm vi kỹ thuật
- Service `fraud-service`
- Topic `transfer.events` → `fraud.alerts`

## Yêu cầu chi tiết
1. Kafka Streams topology: group theo tài khoản đích, đếm số tài khoản nguồn khác nhau trong cửa sổ 10 phút
2. Vượt ngưỡng → phát `FraudAlertRaised` (mức độ, lý do, tài khoản)
3. Lưu cảnh báo vào bảng `FRAUD_ALERT`, API `GET /internal/fraud/alerts`

## Acceptance criteria
- [ ] Test bằng `TopologyTestDriver`: 11 nguồn khác nhau chuyển vào 1 tài khoản trong 10 phút → 1 alert
- [ ] 11 lệnh từ cùng 1 nguồn → không có alert

## Ngoài phạm vi
- Tự động khóa tài khoản (BANK-082)

## Phụ thuộc
- [BANK-080: fraud-service: kiểm tra tần suất giao dịch (đồng bộ)](BANK-080-fraud-velocity-check.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
