---
id: BANK-010
epic: E1
title: Keycloak: realm, client và role
priority: High
depends_on: [BANK-002]
status: TODO
---

# BANK-010: Keycloak: realm, client và role

## Bối cảnh nghiệp vụ
Hệ thống có 2 loại người dùng: khách hàng (CUSTOMER) và nhân viên vận hành (ADMIN).

## Phạm vi kỹ thuật
- `infra/docker-compose.yml`
- `infra/keycloak/realm-bank.json`

## Yêu cầu chi tiết
1. Thêm Keycloak vào docker-compose (port 8180), tự import realm `bank` khi khởi động
2. Client `bank-web` (public, dùng để lấy token khi test) và client `bank-gateway`
3. Role: `CUSTOMER`, `ADMIN`
4. Tạo sẵn 2 user mẫu: `customer01` (CUSTOMER), `admin01` (ADMIN)
5. Token có claim `cif` (mã khách hàng) cho user CUSTOMER

## Acceptance criteria
- [ ] Lấy được access token của `customer01` bằng curl (ghi lệnh mẫu vào `infra/README.md`)
- [ ] Decode token thấy role và claim `cif`

## Ngoài phạm vi
- Đăng ký user tự động khi tạo khách hàng

## Phụ thuộc
- [BANK-002: docker-compose hạ tầng: Oracle, Kafka, Redis](../E0-nen-tang/BANK-002-docker-compose-ha-tang.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
