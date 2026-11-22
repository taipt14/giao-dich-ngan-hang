---
id: BANK-012
epic: E1
title: Rate limit tại gateway bằng Redis
priority: Medium
depends_on: [BANK-011]
status: TODO
---

# BANK-012: Rate limit tại gateway bằng Redis

## Bối cảnh nghiệp vụ
Chống spam API, đặc biệt API chuyển khoản.

## Phạm vi kỹ thuật
- Service `api-gateway`

## Yêu cầu chi tiết
1. Dùng `RequestRateLimiter` + Redis, key theo user id
2. Cấu hình: API chuyển khoản 10 request/phút, API khác 100 request/phút (để trong `application.yml`)
3. Vượt giới hạn → 429 với body theo format lỗi chung, code `RATE_LIMIT_EXCEEDED`

## Acceptance criteria
- [ ] Integration test: gửi 11 request chuyển khoản trong 1 phút → request thứ 11 nhận 429
- [ ] 2 user khác nhau không ảnh hưởng hạn mức của nhau

## Ngoài phạm vi
- Hạn mức tiền giao dịch (BANK-043)

## Phụ thuộc
- [BANK-011: API Gateway: routing và xác thực JWT](BANK-011-api-gateway-routing-jwt.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
