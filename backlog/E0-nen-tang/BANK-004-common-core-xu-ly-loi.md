---
id: BANK-004
epic: E0
title: common-core: định dạng lỗi chuẩn và mã lỗi
priority: High
depends_on: [BANK-001]
status: DONE
---

# BANK-004: common-core: định dạng lỗi chuẩn và mã lỗi

## Bối cảnh nghiệp vụ
Mọi service phải trả lỗi cùng một format để client và gateway xử lý thống nhất.

## Phạm vi kỹ thuật
- Module `common/common-core`

## Yêu cầu chi tiết
1. Record `ErrorResponse(code, message, traceId, timestamp, details)`
2. Class `BusinessException(ErrorCode code, String message)` cho lỗi nghiệp vụ và `TechnicalException(ErrorCode code, String message, Throwable cause)` cho lỗi kỹ thuật
3. Interface `ErrorCode` có `code()` và `int httpStatus()` (không phụ thuộc Spring); mỗi service tự định nghĩa enum mã lỗi riêng
4. `GlobalExceptionHandler` (auto-configuration) xử lý: `BusinessException`, lỗi validate (400), lỗi không xác định (500, không lộ stacktrace)
5. Lấy `traceId` từ MDC nếu có

## Acceptance criteria
- [ ] Unit test cho từng loại exception → đúng HTTP status và body
- [ ] Service chỉ cần thêm dependency `common-core` là handler tự hoạt động

## Ngoài phạm vi
- Tracing thật (BANK-070)

## Phụ thuộc
- [BANK-001: Khởi tạo Maven multi-module và parent POM](BANK-001-khoi-tao-maven-multi-module.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
