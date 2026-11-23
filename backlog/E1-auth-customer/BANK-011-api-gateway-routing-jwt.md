---
id: BANK-011
epic: E1
title: API Gateway: routing và xác thực JWT
priority: High
depends_on: [BANK-010]
status: DONE
---

# BANK-011: API Gateway: routing và xác thực JWT

## Bối cảnh nghiệp vụ
Client chỉ gọi vào một điểm duy nhất; mọi request phải được xác thực.

## Phạm vi kỹ thuật
- Service `api-gateway`
- Thêm dependency `common-core` vào `api-gateway` (chỉ dùng `ErrorResponse`, xem mục 4 `kien-truc.md`)

## Yêu cầu chi tiết
1. Spring Cloud Gateway, route theo prefix: `/api/v1/customers/**` → customer-service, `/api/v1/accounts/**` → account-service, `/api/v1/transfers/**` → transfer-service
2. Xác thực JWT với Keycloak (OAuth2 Resource Server)
3. Chuyển tiếp header `X-User-Id`, `X-Cif`, `X-Roles` xuống service phía sau
4. Đường dẫn `/actuator/health` không cần token
5. Lỗi do gateway tự trả (401, 403, 404 không có route, 503 service phía sau không phản hồi) có body theo record `ErrorResponse` của `common-core` (package `vn.bank.common.error`). Gateway chạy WebFlux nên không dùng được `GlobalExceptionHandler` (chỉ cho servlet), phải viết handler WebFlux riêng trong `api-gateway`. Lý do: client chỉ phải xử lý một format lỗi, dù lỗi đến từ gateway hay từ service. BANK-012 dùng lại handler này cho 429.

## Acceptance criteria
- [ ] Không có token → 401, body `ErrorResponse` có `code` = `UNAUTHORIZED`, có `timestamp`
- [ ] Token hết hạn → 401
- [ ] Token hợp lệ → request tới đúng service, service nhận được header `X-Cif`
- [ ] Gọi path không có route → 404, body `ErrorResponse` có `code` = `NOT_FOUND`

## Ngoài phạm vi
- Rate limit (BANK-012)
- Phân quyền chi tiết trong từng service

## Phụ thuộc
- [BANK-010: Keycloak: realm, client và role](BANK-010-keycloak-realm.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
