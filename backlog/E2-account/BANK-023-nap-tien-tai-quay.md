---
id: BANK-023
epic: E2
title: Nạp tiền tại quầy (ADMIN)
priority: High
depends_on: [BANK-031]
status: TODO
---

# BANK-023: Nạp tiền tại quầy (ADMIN)

## Bối cảnh nghiệp vụ
Nhân viên nạp tiền mặt vào tài khoản khách hàng. Bút toán: Nợ TK tiền mặt nội bộ / Có TK khách hàng.

## Phạm vi kỹ thuật
- Service `account-service`

## Yêu cầu chi tiết
1. `POST /api/v1/accounts/{accountNo}/deposits` (ADMIN), header `Idempotency-Key`, body: `amount`, `description`
2. Số tiền > 0 và ≤ 500.000.000 VND
3. Gọi API posting nội bộ của BANK-031 với tài khoản GL `CASH_VAULT`
4. Phát event `BalanceChanged`

## Acceptance criteria
- [ ] Nạp 1.000.000 → số dư tăng đúng 1.000.000, có 2 dòng ledger cân nhau
- [ ] Gửi lại cùng `Idempotency-Key` → không nạp lần 2, trả kết quả cũ
- [ ] Tài khoản LOCKED/CLOSED → 422 `ACCOUNT_NOT_ACTIVE`

## Ngoài phạm vi
- Rút tiền mặt

## Phụ thuộc
- [BANK-031: API posting nội bộ: ghi bút toán và cập nhật số dư](../E3-ledger/BANK-031-api-posting-noi-bo.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
