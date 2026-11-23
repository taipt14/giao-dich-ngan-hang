---
id: BANK-014
epic: E1
title: customer-service: tra cứu, cập nhật và đổi trạng thái khách hàng
priority: Medium
depends_on: [BANK-013]
status: DONE
---

# BANK-014: customer-service: tra cứu, cập nhật và đổi trạng thái khách hàng

## Bối cảnh nghiệp vụ
Khách hàng xem thông tin của chính mình. ADMIN có thể cập nhật thông tin liên hệ và khóa khách hàng.

## Phạm vi kỹ thuật
- Service `customer-service`
- Event `CustomerStatusChanged`

## Yêu cầu chi tiết
1. `GET /api/v1/customers/me` (CUSTOMER, lấy CIF từ header `X-Cif`)
2. `GET /api/v1/customers/{cif}` (ADMIN)
3. `PATCH /api/v1/customers/{cif}` (ADMIN): chỉ cho sửa SĐT, email, địa chỉ
4. `POST /api/v1/customers/{cif}/status` (ADMIN): `ACTIVE` ↔ `LOCKED`, bắt buộc có lý do
5. Dùng optimistic lock (`VERSION`)
6. API nội bộ `GET /internal/customers/{cif}` cho service khác kiểm tra trạng thái

## Acceptance criteria
- [ ] CUSTOMER không xem được CIF của người khác → 403
- [ ] Sửa trường không được phép (họ tên, CCCD) → 400
- [ ] Đổi trạng thái → phát event `CustomerStatusChanged`
- [ ] 2 request cập nhật đồng thời → 1 request nhận 409 `CONCURRENT_MODIFICATION`

## Ngoài phạm vi
- Lịch sử thay đổi (audit log)

## Phụ thuộc
- [BANK-013: customer-service: đăng ký khách hàng (CIF)](BANK-013-dang-ky-khach-hang.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
