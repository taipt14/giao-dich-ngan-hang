---
id: BANK-074
epic: E7
title: Test end-to-end toàn hệ thống
priority: Medium
depends_on: [BANK-054, BANK-062]
status: TODO
---

# BANK-074: Test end-to-end toàn hệ thống

## Bối cảnh nghiệp vụ
Kiểm tra các luồng chính chạy đúng khi tất cả service cùng hoạt động.

## Phạm vi kỹ thuật
- Module mới `e2e-tests`

## Yêu cầu chi tiết
1. Khởi động toàn bộ hệ thống bằng Testcontainers `ComposeContainer` hoặc docker-compose
2. Kịch bản: tạo khách hàng → mở tài khoản → nạp tiền → chuyển nội bộ → chuyển liên ngân hàng thành công → chuyển liên ngân hàng thất bại (hoàn tiền) → kiểm tra thông báo
3. Chạy bằng profile Maven riêng: `mvn verify -Pe2e`

## Acceptance criteria
- [ ] Tất cả kịch bản pass
- [ ] Có README hướng dẫn chạy

## Ngoài phạm vi
- Test giao diện

## Phụ thuộc
- [BANK-054: Timeout và tra soát giao dịch treo](../E5-lien-ngan-hang/BANK-054-timeout-tra-soat.md)
- [BANK-062: Retry, DLQ và replay message lỗi](../E6-notification/BANK-062-retry-dlq-replay.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
