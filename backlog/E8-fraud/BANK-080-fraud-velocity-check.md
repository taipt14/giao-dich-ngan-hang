---
id: BANK-080
epic: E8
title: fraud-service: kiểm tra tần suất giao dịch (đồng bộ)
priority: Low
depends_on: [BANK-045]
status: TODO
---

# BANK-080: fraud-service: kiểm tra tần suất giao dịch (đồng bộ)

## Bối cảnh nghiệp vụ
Chặn giao dịch khi một tài khoản chuyển quá nhiều lần trong thời gian ngắn (ví dụ quá 5 lệnh trong 1 phút) hoặc số tiền lớn bất thường so với lịch sử.

## Phạm vi kỹ thuật
- Service `fraud-service`
- Redis key `fraud:velocity:*`
- transfer-service gọi trước khi thực hiện

## Yêu cầu chi tiết
1. API nội bộ `POST /internal/fraud/check` trả `ALLOW`, `REVIEW` hoặc `BLOCK` kèm lý do
2. Sliding window bằng Redis sorted set (ZADD, ZREMRANGEBYSCORE, ZCARD trong Lua script)
3. transfer-service gọi trước bước posting; `BLOCK` → FAILED `FRAUD_SUSPECTED`
4. fraud-service lỗi/timeout → **mặc định ALLOW** và log WARN (fail-open, ghi rõ quyết định trong PR)

## Acceptance criteria
- [ ] Lệnh thứ 6 trong 1 phút → BLOCK
- [ ] Sau 1 phút → được chuyển lại
- [ ] Tắt fraud-service → chuyển khoản vẫn hoạt động

## Ngoài phạm vi
- Machine learning

## Phụ thuộc
- [BANK-045: Thực hiện chuyển khoản nội bộ](../E4-chuyen-khoan-noi-bo/BANK-045-thuc-hien-chuyen-khoan-noi-bo.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
