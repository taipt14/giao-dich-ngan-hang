---
id: BANK-044
epic: E4
title: Tính phí giao dịch
priority: Medium
depends_on: [BANK-040]
status: TODO
---

# BANK-044: Tính phí giao dịch

## Bối cảnh nghiệp vụ
Chuyển nội bộ miễn phí. Chuyển liên ngân hàng: dưới 500.000 phí 0; từ 500.000 trở lên phí 0,02% (tối thiểu 2.200, tối đa 11.000). Phí đã gồm VAT.

## Phạm vi kỹ thuật
- Service `transfer-service`
- Bảng `FEE_SCHEDULE`

## Yêu cầu chi tiết
1. Bảng biểu phí theo loại giao dịch và khoảng số tiền, có ngày hiệu lực
2. Lớp `FeeCalculator` thuần (không phụ thuộc Spring) để dễ unit test
3. Phí tính khi tạo lệnh, lưu vào `TRANSFER.FEE`
4. Kiểm tra số dư khả dụng ≥ amount + fee

## Acceptance criteria
- [ ] Unit test các mốc: 499.999 → 0; 500.000 → 2.200; 20.000.000 → 4.000; 100.000.000 → 11.000
- [ ] Làm tròn HALF_UP đến đơn vị đồng
- [ ] Đổi biểu phí có ngày hiệu lực trong tương lai → lệnh hôm nay vẫn dùng biểu phí cũ

## Ngoài phạm vi
- Miễn phí theo chương trình khuyến mãi

## Phụ thuộc
- [BANK-040: transfer-service: bảng TRANSFER và máy trạng thái](BANK-040-bang-transfer-state-machine.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
