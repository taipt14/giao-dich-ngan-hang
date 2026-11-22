---
id: BANK-045
epic: E4
title: Thực hiện chuyển khoản nội bộ
priority: High
depends_on: [BANK-032, BANK-042, BANK-043, BANK-044]
status: TODO
---

# BANK-045: Thực hiện chuyển khoản nội bộ

## Bối cảnh nghiệp vụ
Sau khi xác thực OTP, trừ tiền tài khoản nguồn, cộng tiền tài khoản đích. Bút toán: Nợ TK nguồn / Có TK đích.

## Phạm vi kỹ thuật
- Service `transfer-service`
- REST client gọi `POST /internal/postings` của account-service

## Yêu cầu chi tiết
1. Gọi posting với `referenceType=TRANSFER`, `referenceId=transferId`
2. Posting thành công → `SUCCESS`; lỗi nghiệp vụ (không đủ số dư, tài khoản khóa) → `FAILED` kèm `FAILURE_CODE`
3. Lỗi kỹ thuật (timeout, 5xx) → retry tối đa 3 lần (an toàn vì posting idempotent); vẫn lỗi → trạng thái `UNKNOWN`, chờ tra soát
4. Không bao giờ chuyển `FAILED` khi chưa chắc tiền chưa bị trừ

## Acceptance criteria
- [ ] E2E: A có 1.000.000 chuyển 300.000 cho B → A còn 700.000, B tăng 300.000, lệnh SUCCESS
- [ ] Không đủ số dư → FAILED `INSUFFICIENT_BALANCE`, hạn mức được hoàn
- [ ] Giả lập account-service timeout nhưng thực tế đã ghi → retry không trừ tiền lần 2
- [ ] Lệnh UNKNOWN có thể tra soát lại bằng `GET /internal/postings?referenceId=`

## Ngoài phạm vi
- Phát event (BANK-046)
- Liên ngân hàng (E5)

## Phụ thuộc
- [BANK-032: Trừ số dư an toàn khi có nhiều giao dịch đồng thời](../E3-ledger/BANK-032-tru-so-du-an-toan-concurrency.md)
- [BANK-042: Xác thực OTP bằng Redis](BANK-042-otp-redis.md)
- [BANK-043: Hạn mức giao dịch theo lần và theo ngày](BANK-043-han-muc-giao-dich.md)
- [BANK-044: Tính phí giao dịch](BANK-044-tinh-phi-giao-dich.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
