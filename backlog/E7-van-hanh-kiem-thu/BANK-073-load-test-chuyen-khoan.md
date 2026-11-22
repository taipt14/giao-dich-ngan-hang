---
id: BANK-073
epic: E7
title: Test tải và test đồng thời cho chuyển khoản
priority: Medium
depends_on: [BANK-045]
status: TODO
---

# BANK-073: Test tải và test đồng thời cho chuyển khoản

## Bối cảnh nghiệp vụ
Chứng minh hệ thống đúng về tiền khi có tải cao.

## Phạm vi kỹ thuật
- Thư mục `load-test/` (Gatling hoặc k6)

## Yêu cầu chi tiết
1. Kịch bản: 100 tài khoản, mỗi tài khoản 1.000.000; 50 user ảo chuyển ngẫu nhiên giữa các tài khoản trong 5 phút
2. Sau khi chạy: kiểm tra tổng số dư 100 tài khoản không đổi và đối soát (BANK-034) không lệch
3. Ghi kết quả (throughput, p95, p99, tỉ lệ lỗi) vào `docs/load-test-result.md`

## Acceptance criteria
- [ ] Tổng tiền hệ thống không đổi sau test
- [ ] Không có deadlock trong log Oracle
- [ ] Có báo cáo kết quả

## Ngoài phạm vi
- Tối ưu hiệu năng (tạo task riêng nếu phát hiện vấn đề)

## Phụ thuộc
- [BANK-045: Thực hiện chuyển khoản nội bộ](../E4-chuyen-khoan-noi-bo/BANK-045-thuc-hien-chuyen-khoan-noi-bo.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
