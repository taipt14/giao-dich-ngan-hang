---
id: BANK-042
epic: E4
title: Xác thực OTP bằng Redis
priority: High
depends_on: [BANK-041]
status: DONE
---

# BANK-042: Xác thực OTP bằng Redis

## Bối cảnh nghiệp vụ
Lệnh chuyển khoản cần OTP. OTP có hiệu lực 3 phút, nhập sai quá 5 lần thì hủy lệnh.

## Phạm vi kỹ thuật
- Service `transfer-service`
- Redis key `otp:*`, `otp:fail:*`

## Yêu cầu chi tiết
1. Sau khi tạo lệnh: sinh OTP 6 số bằng `SecureRandom`, lưu **hash** (SHA-256 + transferId) vào Redis TTL 3 phút, chuyển trạng thái `OTP_PENDING`
2. Gửi OTP: ở môi trường dev chỉ log ra (đánh dấu rõ là DEV), không trả trong response
3. `POST /api/v1/transfers/{id}/confirm` body `{otp}`
4. Sai OTP → tăng bộ đếm; sai lần thứ 5 → lệnh FAILED với `OTP_MAX_ATTEMPTS`
5. `POST /api/v1/transfers/{id}/otp/resend`: tối đa 3 lần, cách nhau tối thiểu 30 giây
6. Đúng OTP → xóa key OTP (dùng một lần), chuyển `PROCESSING`

## Acceptance criteria
- [ ] OTP hết hạn → 422 `OTP_EXPIRED`
- [ ] Dùng lại OTP đã xác thực → 422
- [ ] Sai 5 lần → lệnh FAILED, nhập đúng sau đó vẫn bị từ chối
- [ ] Kiểm tra log: không có OTP dạng rõ ở môi trường khác dev

## Ngoài phạm vi
- Gửi SMS thật
- Smart OTP / sinh trắc học

## Phụ thuộc
- [BANK-041: API khởi tạo chuyển khoản với Idempotency-Key](BANK-041-api-khoi-tao-chuyen-khoan-idempotency.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
