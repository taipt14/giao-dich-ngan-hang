---
id: BANK-020
epic: E2
title: account-service: mở tài khoản thanh toán
priority: High
depends_on: [BANK-014]
status: TODO
---

# BANK-020: account-service: mở tài khoản thanh toán

## Bối cảnh nghiệp vụ
Khách hàng ACTIVE được mở tối đa 3 tài khoản thanh toán VND. Số tài khoản 12 chữ số, không trùng.

## Phạm vi kỹ thuật
- Service `account-service`
- Bảng `ACCOUNT`
- Event `AccountOpened` → `account.events`

## Yêu cầu chi tiết
1. API `POST /api/v1/accounts` (ADMIN), body: `cif`, `accountType=CURRENT`
2. Gọi `GET /internal/customers/{cif}` để kiểm tra khách hàng tồn tại và ACTIVE
3. Sinh số tài khoản: tiền tố `1000` + 8 số từ sequence
4. Bảng `ACCOUNT`: ACCOUNT_NO, CIF, ACCOUNT_TYPE, CURRENCY, BALANCE `NUMBER(19,2)`, AVAILABLE_BALANCE, STATUS, VERSION, CREATED_AT, UPDATED_AT
5. Ràng buộc DB: `CHECK (BALANCE >= 0)`
6. Số dư ban đầu bằng 0, trạng thái `ACTIVE`

## Acceptance criteria
- [ ] Mở thành công → 201, trả số tài khoản
- [ ] Khách hàng không tồn tại → 404 `CUSTOMER_NOT_FOUND`; bị khóa → 422 `CUSTOMER_NOT_ACTIVE`
- [ ] Mở tài khoản thứ 4 → 422 `ACCOUNT_LIMIT_EXCEEDED`
- [ ] Phát event `AccountOpened`

## Ngoài phạm vi
- Tài khoản tiết kiệm, ngoại tệ
- Nạp tiền (BANK-023)

## Phụ thuộc
- [BANK-014: customer-service: tra cứu, cập nhật và đổi trạng thái khách hàng](../E1-auth-customer/BANK-014-tra-cuu-cap-nhat-khach-hang.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
