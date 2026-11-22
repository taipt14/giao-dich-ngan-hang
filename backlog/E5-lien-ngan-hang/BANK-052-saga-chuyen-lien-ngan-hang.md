---
id: BANK-052
epic: E5
title: Saga chuyển liên ngân hàng: trừ tiền và gửi NAPAS
priority: High
depends_on: [BANK-045, BANK-046, BANK-050]
status: TODO
---

# BANK-052: Saga chuyển liên ngân hàng: trừ tiền và gửi NAPAS

## Bối cảnh nghiệp vụ
Chuyển liên ngân hàng gồm nhiều bước ở nhiều hệ thống nên không dùng được một transaction. Dùng Saga (orchestration): transfer-service điều phối.

## Phạm vi kỹ thuật
- Service `transfer-service`
- Bảng `SAGA_STEP` (tùy chọn)

## Yêu cầu chi tiết
1. Bước 1: posting Nợ TK khách hàng (amount + fee) / Có `NAPAS_SETTLEMENT` (amount) / Có `FEE_INCOME` (fee)
2. Bước 2: ghi outbox event `InterbankTransferRequested` → `napas.requests`, trạng thái `PENDING_NAPAS`
3. Bước 1 lỗi nghiệp vụ → FAILED, dừng saga
4. Vẽ sơ đồ sequence (Mermaid) của saga vào `docs/saga-lien-ngan-hang.md`, gồm cả các nhánh lỗi

## Acceptance criteria
- [ ] Sau bước 2: tiền khách hàng đã bị trừ, lệnh ở PENDING_NAPAS, event có trên topic
- [ ] Bước 1 thất bại → không có event nào gửi NAPAS

## Ngoài phạm vi
- Xử lý phản hồi (BANK-053)
- Timeout (BANK-054)

## Phụ thuộc
- [BANK-045: Thực hiện chuyển khoản nội bộ](../E4-chuyen-khoan-noi-bo/BANK-045-thuc-hien-chuyen-khoan-noi-bo.md)
- [BANK-046: Outbox pattern cho event nghiệp vụ](../E4-chuyen-khoan-noi-bo/BANK-046-outbox-pattern.md)
- [BANK-050: mock-napas-service: giả lập ngân hàng đối tác](BANK-050-mock-napas-service.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
