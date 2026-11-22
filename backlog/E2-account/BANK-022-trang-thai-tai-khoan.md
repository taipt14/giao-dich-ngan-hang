---
id: BANK-022
epic: E2
title: Khóa, mở khóa và đóng tài khoản
priority: Medium
depends_on: [BANK-020]
status: TODO
---

# BANK-022: Khóa, mở khóa và đóng tài khoản

## Bối cảnh nghiệp vụ
Tài khoản có các trạng thái: ACTIVE, LOCKED (tạm khóa), CLOSED (đã đóng, không mở lại). Chỉ đóng được khi số dư bằng 0.

## Phạm vi kỹ thuật
- Service `account-service`
- Event `AccountStatusChanged`

## Yêu cầu chi tiết
1. `POST /api/v1/accounts/{accountNo}/lock|unlock|close` (ADMIN), bắt buộc có lý do
2. Chuyển trạng thái hợp lệ: ACTIVE→LOCKED, LOCKED→ACTIVE, ACTIVE→CLOSED, LOCKED→CLOSED. Còn lại báo lỗi
3. Cài đặt state machine trong lớp domain (enum có phương thức `canTransitionTo`)
4. Consume `CustomerStatusChanged`: khách hàng bị LOCKED → khóa toàn bộ tài khoản ACTIVE của khách hàng đó
5. Bảng `ACCOUNT_STATUS_HISTORY` lưu lịch sử đổi trạng thái

## Acceptance criteria
- [ ] Unit test đầy đủ bảng chuyển trạng thái (hợp lệ và không hợp lệ)
- [ ] Đóng tài khoản còn tiền → 422 `ACCOUNT_BALANCE_NOT_ZERO`
- [ ] Từ CLOSED chuyển sang trạng thái khác → 422 `INVALID_STATUS_TRANSITION`
- [ ] Consumer idempotent: nhận cùng event 2 lần chỉ ghi 1 dòng history

## Ngoài phạm vi
- Phong tỏa một phần số dư

## Phụ thuộc
- [BANK-020: account-service: mở tài khoản thanh toán](BANK-020-mo-tai-khoan-thanh-toan.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
