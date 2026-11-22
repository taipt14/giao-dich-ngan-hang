---
id: BANK-033
epic: E3
title: Sao kê tài khoản
priority: Medium
depends_on: [BANK-031]
status: TODO
---

# BANK-033: Sao kê tài khoản

## Bối cảnh nghiệp vụ
Khách hàng xem lịch sử biến động số dư theo khoảng thời gian.

## Phạm vi kỹ thuật
- Service `account-service`

## Yêu cầu chi tiết
1. `GET /api/v1/accounts/{accountNo}/statements?from=&to=&page=&size=`
2. Khoảng thời gian tối đa 90 ngày, mặc định 30 ngày gần nhất
3. Mỗi dòng: thời gian, loại giao dịch, mô tả, số tiền (+/-), số dư sau giao dịch
4. Phân trang theo keyset (ENTRY_ID), sắp xếp mới nhất trước

## Acceptance criteria
- [ ] Khoảng thời gian > 90 ngày → 400 `STATEMENT_RANGE_TOO_LARGE`
- [ ] CUSTOMER xem sao kê tài khoản người khác → 403
- [ ] Query dùng index `(ACCOUNT_NO, CREATED_AT)` (đính kèm explain plan trong PR)

## Ngoài phạm vi
- Xuất file PDF/Excel

## Phụ thuộc
- [BANK-031: API posting nội bộ: ghi bút toán và cập nhật số dư](BANK-031-api-posting-noi-bo.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
