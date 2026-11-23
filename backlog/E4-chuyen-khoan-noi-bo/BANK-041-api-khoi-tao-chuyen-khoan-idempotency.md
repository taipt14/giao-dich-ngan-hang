---
id: BANK-041
epic: E4
title: API khởi tạo chuyển khoản với Idempotency-Key
priority: High
depends_on: [BANK-040]
status: DONE
---

# BANK-041: API khởi tạo chuyển khoản với Idempotency-Key

## Bối cảnh nghiệp vụ
Khách hàng tạo lệnh chuyển khoản. Nếu mạng chập chờn, app có thể gửi lại request; hệ thống không được tạo 2 lệnh.

## Phạm vi kỹ thuật
- Service `transfer-service`
- Redis key `idem:*`

## Yêu cầu chi tiết
1. `POST /api/v1/transfers`, header `Idempotency-Key` (UUID) bắt buộc. Body: sourceAccount, destAccount, destBankCode (null nếu nội bộ), amount, description
2. Kiểm tra tài khoản nguồn thuộc `X-Cif` (gọi account-service)
3. Kiểm tra tài khoản đích tồn tại và ACTIVE (với chuyển nội bộ)
4. Idempotency 2 lớp: Redis `SET NX` (TTL 24h) + unique `(CIF, IDEMPOTENCY_KEY)` trong DB
5. Cùng key, cùng nội dung → trả lệnh cũ; cùng key, **khác nội dung** → 422 `IDEMPOTENCY_KEY_REUSED`
6. Lệnh được tạo ở trạng thái `INITIATED`, response trả `transferId` và phí dự kiến

## Acceptance criteria
- [ ] Thiếu header → 400 `IDEMPOTENCY_KEY_REQUIRED`
- [ ] Gửi đồng thời 5 request cùng key → chỉ 1 lệnh được tạo
- [ ] Chuyển cho chính tài khoản nguồn → 422 `SAME_ACCOUNT_TRANSFER`
- [ ] amount ≤ 0 hoặc có quá 2 chữ số thập phân → 400

## Ngoài phạm vi
- OTP (BANK-042)
- Thực hiện trừ tiền (BANK-045)

## Phụ thuộc
- [BANK-040: transfer-service: bảng TRANSFER và máy trạng thái](BANK-040-bang-transfer-state-machine.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
