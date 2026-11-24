---
id: BANK-040
epic: E4
title: transfer-service: bảng TRANSFER và máy trạng thái
priority: High
depends_on: [BANK-003, BANK-004]
status: DONE
---

# BANK-040: transfer-service: bảng TRANSFER và máy trạng thái

## Bối cảnh nghiệp vụ
Mỗi lệnh chuyển khoản đi qua các trạng thái: INITIATED → OTP_PENDING → PROCESSING → SUCCESS / FAILED. Riêng liên ngân hàng có thêm PENDING_NAPAS, REVERSED, UNKNOWN.

## Phạm vi kỹ thuật
- Service `transfer-service`
- Bảng `TRANSFER`, `TRANSFER_STATUS_HISTORY`

## Yêu cầu chi tiết
1. Bảng `TRANSFER`: TRANSFER_ID, TYPE (INTERNAL/INTERBANK), SOURCE_ACCOUNT, DEST_ACCOUNT, DEST_BANK_CODE, AMOUNT, FEE, DESCRIPTION, STATUS, FAILURE_CODE, CIF, IDEMPOTENCY_KEY, VERSION, CREATED_AT, UPDATED_AT
2. Enum `TransferStatus` định nghĩa các chuyển trạng thái hợp lệ
3. Mọi lần đổi trạng thái ghi vào `TRANSFER_STATUS_HISTORY`
4. Vẽ sơ đồ trạng thái (Mermaid) trong `docs/transfer-state.md`

## Acceptance criteria
- [ ] Unit test toàn bộ chuyển trạng thái hợp lệ và không hợp lệ
- [ ] SUCCESS, FAILED, REVERSED là trạng thái cuối: không chuyển tiếp được nữa

## Ngoài phạm vi
- API (BANK-041)

## Phụ thuộc
- [BANK-003: Cấu hình datasource Oracle và Flyway cho các service](../E0-nen-tang/BANK-003-flyway-ket-noi-oracle.md)
- [BANK-004: common-core: định dạng lỗi chuẩn và mã lỗi](../E0-nen-tang/BANK-004-common-core-xu-ly-loi.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
