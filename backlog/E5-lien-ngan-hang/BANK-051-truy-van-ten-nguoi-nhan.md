---
id: BANK-051
epic: E5
title: Truy vấn tên người nhận liên ngân hàng
priority: Medium
depends_on: [BANK-050, BANK-055]
status: TODO
---

# BANK-051: Truy vấn tên người nhận liên ngân hàng

## Bối cảnh nghiệp vụ
Trước khi chuyển liên ngân hàng, app hiển thị tên chủ tài khoản đích để khách hàng xác nhận.

## Phạm vi kỹ thuật
- transfer-service, mock-napas-service

## Yêu cầu chi tiết
1. mock-napas: `GET /napas/accounts/{bankCode}/{accountNo}` trả tên chủ tài khoản
2. transfer-service: `GET /api/v1/transfers/beneficiary?bankCode=&accountNo=`
3. Cache kết quả trong Redis 10 phút
4. Gọi qua client có circuit breaker (BANK-055)

## Acceptance criteria
- [ ] Tài khoản không tồn tại → 404 `BENEFICIARY_NOT_FOUND`
- [ ] mock-napas tắt → 503 `NAPAS_UNAVAILABLE` trong vòng 3 giây (không treo request)

## Ngoài phạm vi
- Truy vấn nội bộ (đã có ở account-service)

## Phụ thuộc
- [BANK-050: mock-napas-service: giả lập ngân hàng đối tác](BANK-050-mock-napas-service.md)
- [BANK-055: Resilience4j cho các lời gọi REST giữa service](BANK-055-resilience4j.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
