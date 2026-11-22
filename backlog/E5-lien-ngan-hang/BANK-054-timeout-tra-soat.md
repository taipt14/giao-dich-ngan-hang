---
id: BANK-054
epic: E5
title: Timeout và tra soát giao dịch treo
priority: High
depends_on: [BANK-053]
status: TODO
---

# BANK-054: Timeout và tra soát giao dịch treo

## Bối cảnh nghiệp vụ
NAPAS có thể không trả lời. Không được tự ý hoàn tiền khi chưa biết kết quả, vì có thể người nhận đã nhận được tiền.

## Phạm vi kỹ thuật
- Service `transfer-service`

## Yêu cầu chi tiết
1. Job chạy mỗi phút: tìm lệnh `PENDING_NAPAS` quá 5 phút
2. Gọi API tra soát của mock-napas: có kết quả → xử lý như BANK-053; chưa có → giữ nguyên, tăng số lần tra soát
3. Tra soát quá 10 lần không có kết quả → `UNKNOWN`, cảnh báo cho vận hành (log ERROR + API danh sách)
4. API `GET /internal/transfers/unknown` và `POST /internal/transfers/{id}/resolve` (ADMIN xử lý tay: SUCCESS hoặc REVERSE)
5. Dùng `SKIP LOCKED` để chạy nhiều instance an toàn

## Acceptance criteria
- [ ] Đuôi `0002` (chậm 2 phút) → lệnh vẫn kết thúc SUCCESS, không bị hoàn nhầm
- [ ] Đuôi `0003` (không trả lời) → sau khoảng thời gian cấu hình lệnh chuyển UNKNOWN, tiền chưa bị hoàn
- [ ] ADMIN resolve REVERSE → khách hàng được hoàn tiền đúng 1 lần

## Ngoài phạm vi
- Giờ cut-off, ngày nghỉ

## Phụ thuộc
- [BANK-053: Xử lý phản hồi NAPAS và hoàn tiền (bù trừ)](BANK-053-xu-ly-phan-hoi-napas-bu-tru.md)

## Ghi chú cho agent
Đây là task quan trọng nhất về mặt nghiệp vụ. Nếu thiết kế có điểm nào chưa rõ, agent phải hỏi lại trước khi code.

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
