---
id: BANK-075
epic: E7
title: README và tài liệu dự án
priority: Low
depends_on: [BANK-074]
status: TODO
---

# BANK-075: README và tài liệu dự án

## Bối cảnh nghiệp vụ
Người xem repo (nhà tuyển dụng, reviewer) cần hiểu dự án trong 5 phút.

## Phạm vi kỹ thuật
- `README.md` gốc của source code
- `docs/`

## Yêu cầu chi tiết
1. Giới thiệu, sơ đồ kiến trúc (Mermaid), công nghệ, cách chạy bằng 1 lệnh
2. Liệt kê các bài toán kỹ thuật đã giải quyết: idempotency, concurrency, outbox, saga, tra soát, DLQ
3. Bộ request mẫu (file `.http` hoặc Postman collection)

## Acceptance criteria
- [ ] Người mới clone repo và chạy được theo README không cần hỏi thêm

## Ngoài phạm vi
- Deploy lên cloud

## Phụ thuộc
- [BANK-074: Test end-to-end toàn hệ thống](BANK-074-test-e2e-toan-he-thong.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
