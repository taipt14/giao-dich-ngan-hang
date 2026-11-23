# Máy trạng thái lệnh chuyển khoản

> Service: `transfer-service`, migration `V2__create_transfer_and_transfer_status_history_tables.sql` (BANK-040).
> Code: enum `TransferStatus` (bảng chuyển hợp lệ), `TransferType#supports` (trạng thái theo loại lệnh), entity `Transfer`.

## 1. Sơ đồ

```mermaid
stateDiagram-v2
    [*] --> INITIATED: tạo lệnh (BANK-041)
    INITIATED --> OTP_PENDING: sinh và gửi OTP (BANK-042)
    OTP_PENDING --> PROCESSING: OTP đúng (BANK-042)
    OTP_PENDING --> FAILED: sai OTP quá số lần (OTP_MAX_ATTEMPTS) / OTP đúng nhưng vượt hạn mức ngày (DAILY_LIMIT_EXCEEDED, BANK-043)

    PROCESSING --> SUCCESS: posting nội bộ thành công (BANK-045)
    PROCESSING --> FAILED: lỗi nghiệp vụ khi posting (BANK-045, BANK-052)
    PROCESSING --> UNKNOWN: lỗi kỹ thuật, hết retry (BANK-045)
    PROCESSING --> PENDING_NAPAS: liên ngân hàng đã trừ tiền, gửi NAPAS (BANK-052)

    PENDING_NAPAS --> SUCCESS: NAPAS trả SUCCESS (BANK-053)
    PENDING_NAPAS --> REVERSED: NAPAS trả FAILED, đã hoàn tiền (BANK-053)
    PENDING_NAPAS --> UNKNOWN: tra soát quá số lần (BANK-054)

    UNKNOWN --> SUCCESS: tra soát / ADMIN xác nhận thành công
    UNKNOWN --> FAILED: chắc chắn tiền chưa bị trừ
    UNKNOWN --> REVERSED: ADMIN chọn REVERSE, đã hoàn tiền (BANK-054)

    SUCCESS --> [*]
    FAILED --> [*]
    REVERSED --> [*]
```

## 2. Quy tắc

| Quy tắc | Chi tiết |
|---|---|
| Trạng thái cuối | `SUCCESS`, `FAILED`, `REVERSED`: không chuyển tiếp được nữa |
| `UNKNOWN` | **Không** phải trạng thái cuối: chờ tra soát hoặc ADMIN xử lý tay |
| Chỉ liên ngân hàng | `PENDING_NAPAS`, `REVERSED`. Lệnh `INTERNAL` không bao giờ vào hai trạng thái này |
| `FAILED` | Chỉ khi **chắc chắn** tiền chưa bị trừ, luôn kèm `FAILURE_CODE` (CHECK `CK_TRANSFER_FAILURE_CODE`). Tiền đã trừ mà giao dịch không thành → `REVERSED` sau khi hoàn tiền |
| Giữ nguyên trạng thái | Không được tính là một lần chuyển (ví dụ gửi lại OTP vẫn ở `OTP_PENDING`, không ghi lịch sử) |
| Chuyển không hợp lệ | `BusinessException` mã `INVALID_STATUS_TRANSITION` (HTTP 422), trạng thái và lịch sử không đổi |

## 3. Lịch sử trạng thái

Mọi lần đổi trạng thái, kể cả lúc tạo lệnh, thêm một dòng `TRANSFER_STATUS_HISTORY`:

| Cột | Ý nghĩa |
|---|---|
| `PREVIOUS_STATUS` | Trạng thái trước; `NULL` ở dòng đầu tiên (lệnh vừa tạo, `STATUS = INITIATED`) |
| `STATUS` | Trạng thái sau |
| `FAILURE_CODE` | Mã lỗi khi chuyển sang `FAILED` |
| `CHANGED_AT` | Thời điểm đổi (UTC), bằng `TRANSFER.UPDATED_AT` của lần đổi đó |

Dòng lịch sử do entity `Transfer` tạo trong `changeStatus` / `fail` và được ghi xuống DB cùng transaction với lệnh
(quan hệ `@OneToMany` cascade `PERSIST`), nên code ở tầng `application` không thể đổi trạng thái mà quên ghi lịch sử.
