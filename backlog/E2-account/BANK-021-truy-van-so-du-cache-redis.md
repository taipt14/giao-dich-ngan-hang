---
id: BANK-021
epic: E2
title: Truy vấn số dư và danh sách tài khoản (cache Redis)
priority: High
depends_on: [BANK-020]
status: TODO
---

# BANK-021: Truy vấn số dư và danh sách tài khoản (cache Redis)

## Bối cảnh nghiệp vụ
Khách hàng xem danh sách tài khoản và số dư của mình. Đây là API được gọi rất nhiều.

## Phạm vi kỹ thuật
- Service `account-service`
- Redis key `balance:{accountNo}`

## Yêu cầu chi tiết
1. `GET /api/v1/accounts` (CUSTOMER): danh sách tài khoản theo `X-Cif`
2. `GET /api/v1/accounts/{accountNo}/balance`: số dư thực tế và số dư khả dụng
3. Cache số dư trong Redis TTL 5 phút (cache-aside)
4. Xóa cache ngay sau khi transaction thay đổi số dư **commit thành công** (dùng `TransactionSynchronization.afterCommit`)
5. Redis lỗi → vẫn đọc được từ DB (không fail API)

## Acceptance criteria
- [ ] CUSTOMER xem tài khoản của người khác → 403 `ACCOUNT_ACCESS_DENIED`
- [ ] Gọi lần 2 không truy vấn DB (kiểm tra bằng test)
- [ ] Sau khi số dư thay đổi, lần đọc kế tiếp trả số dư mới
- [ ] Tắt Redis → API vẫn trả 200

## Ngoài phạm vi
- Dùng số dư từ cache để quyết định trừ tiền (KHÔNG được làm vậy)

## Phụ thuộc
- [BANK-020: account-service: mở tài khoản thanh toán](BANK-020-mo-tai-khoan-thanh-toan.md)

## Ghi chú cho agent
Cache chỉ phục vụ hiển thị. Mọi kiểm tra số dư khi trừ tiền phải đọc từ DB có khóa.

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
