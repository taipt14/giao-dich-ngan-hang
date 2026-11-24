---
id: BANK-043
epic: E4
title: Hạn mức giao dịch theo lần và theo ngày
priority: High
depends_on: [BANK-041]
status: DONE
---

# BANK-043: Hạn mức giao dịch theo lần và theo ngày

## Bối cảnh nghiệp vụ
Mỗi khách hàng có hạn mức: tối đa 100 triệu/lần và 500 triệu/ngày (mặc định). Hạn mức được tính lại từ 00:00 mỗi ngày (giờ Việt Nam).

## Phạm vi kỹ thuật
- Service `transfer-service`
- Bảng `TRANSFER_LIMIT_CONFIG`
- Redis key `limit:*`

## Yêu cầu chi tiết
1. Bảng cấu hình hạn mức theo loại khách hàng (mặc định `STANDARD`)
2. Kiểm tra hạn mức khi tạo lệnh (BANK-041) và **giữ chỗ** hạn mức khi xác thực OTP thành công bằng Redis `INCRBY` (atomic, dùng Lua script để kiểm tra và cộng cùng lúc)
3. Lệnh FAILED/REVERSED → hoàn lại hạn mức đã giữ
4. Redis mất dữ liệu → tính lại từ bảng TRANSFER các lệnh SUCCESS/PROCESSING trong ngày
5. Múi giờ: `Asia/Ho_Chi_Minh`

## Acceptance criteria
- [ ] Chuyển 100.000.001 → 422 `PER_TRANSACTION_LIMIT_EXCEEDED`
- [ ] Đã chuyển 450 triệu, chuyển thêm 60 triệu → 422 `DAILY_LIMIT_EXCEEDED`
- [ ] 10 lệnh đồng thời mỗi lệnh 100 triệu → tối đa 5 lệnh vượt qua bước giữ chỗ
- [ ] Lệnh thất bại → hạn mức được hoàn lại

## Ngoài phạm vi
- Khách hàng tự đổi hạn mức

## Phụ thuộc
- [BANK-041: API khởi tạo chuyển khoản với Idempotency-Key](BANK-041-api-khoi-tao-chuyen-khoan-idempotency.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
