---
id: BANK-053
epic: E5
title: Xử lý phản hồi NAPAS và hoàn tiền (bù trừ)
priority: High
depends_on: [BANK-052]
status: TODO
---

# BANK-053: Xử lý phản hồi NAPAS và hoàn tiền (bù trừ)

## Bối cảnh nghiệp vụ
NAPAS trả thành công → hoàn tất. Trả thất bại → hoàn lại toàn bộ tiền (gồm phí) cho khách hàng bằng bút toán đảo.

## Phạm vi kỹ thuật
- Service `transfer-service`

## Yêu cầu chi tiết
1. Consume `napas.responses` (idempotent)
2. SUCCESS → lệnh `SUCCESS`, phát `TransferCompleted`
3. FAILED → posting đảo với `referenceType=REVERSAL`, `referenceId=transferId`: Nợ `NAPAS_SETTLEMENT` + Nợ `FEE_INCOME` / Có TK khách hàng; lệnh `REVERSED`; hoàn hạn mức; phát `TransferFailed`
4. Phản hồi đến khi lệnh đã ở trạng thái cuối → bỏ qua và log WARN
5. Posting đảo lỗi kỹ thuật → retry; hết retry → để message vào DLQ, **không** đánh dấu đã xử lý

## Acceptance criteria
- [ ] Tài khoản đích đuôi `0001` → sau vài giây lệnh REVERSED, số dư khách hàng trở về như ban đầu
- [ ] Nhận phản hồi FAILED 2 lần → chỉ hoàn tiền 1 lần
- [ ] Đối soát (BANK-034) không phát hiện lệch sau khi hoàn tiền

## Ngoài phạm vi
- Hoàn tiền một phần

## Phụ thuộc
- [BANK-052: Saga chuyển liên ngân hàng: trừ tiền và gửi NAPAS](BANK-052-saga-chuyen-lien-ngan-hang.md)

## Tài liệu tham khảo
- [Kiến trúc và quy ước](../../docs/kien-truc.md)
