---
id: BANK-032
epic: E3
title: Trừ số dư an toàn khi có nhiều giao dịch đồng thời
priority: High
depends_on: [BANK-031]
status: TODO
---

# BANK-032: Trừ số dư an toàn khi có nhiều giao dịch đồng thời

## Bối cảnh nghiệp vụ
Nhiều giao dịch có thể cùng trừ tiền một tài khoản cùng lúc. Không được để số dư âm hoặc mất tiền.

## Phạm vi kỹ thuật
- Service `account-service`
- Lớp `PostingService`, `AccountRepository`

## Yêu cầu chi tiết
1. Khóa tài khoản bằng `SELECT ... FOR UPDATE` (JPA `PESSIMISTIC_WRITE`) với timeout 5 giây
2. **Chống deadlock:** khóa các tài khoản theo thứ tự `ACCOUNT_NO` tăng dần
3. Hết thời gian chờ khóa → 503 `ACCOUNT_BUSY` (client được phép retry)
4. Số dư không đủ → 422 `INSUFFICIENT_BALANCE`

## Acceptance criteria
- [ ] Test: tài khoản có 1.000.000, chạy 20 thread mỗi thread trừ 100.000 → đúng 10 thành công, 10 lỗi `INSUFFICIENT_BALANCE`, số dư cuối = 0
- [ ] Test: A chuyển cho B và B chuyển cho A đồng thời 50 lần → không deadlock, tổng tiền A + B không đổi
- [ ] Test dùng Oracle thật (Testcontainers), không dùng H2

## Ngoài phạm vi
- Distributed lock Redis (không cần vì đã khóa ở DB)

## Phụ thuộc
- [BANK-031: API posting nội bộ: ghi bút toán và cập nhật số dư](BANK-031-api-posting-noi-bo.md)

## Ghi chú cho agent
Giải thích trong PR vì sao chọn pessimistic lock thay vì optimistic lock cho trường hợp này.

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
