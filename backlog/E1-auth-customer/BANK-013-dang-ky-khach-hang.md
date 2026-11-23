---
id: BANK-013
epic: E1
title: customer-service: đăng ký khách hàng (CIF)
priority: High
depends_on: [BANK-003, BANK-004, BANK-005]
status: DONE
---

# BANK-013: customer-service: đăng ký khách hàng (CIF)

## Bối cảnh nghiệp vụ
Nhân viên (ADMIN) tạo hồ sơ khách hàng. Mỗi khách hàng có một mã CIF duy nhất, CCCD và số điện thoại không được trùng.

## Phạm vi kỹ thuật
- Service `customer-service`
- Bảng `CUSTOMER`
- Event `CustomerCreated` → `customer.events`
- Thêm dependency `common-core`, `common-events` (và `common-test` scope test) vào `customer-service`, theo bảng ở mục 4 `kien-truc.md`
- Module `common/common-events`: thêm factory method cho `EventEnvelope`

## Yêu cầu chi tiết
1. API `POST /api/v1/customers` (role ADMIN). Body: họ tên, ngày sinh, số CCCD (12 số), số điện thoại, email, địa chỉ
2. Sinh CIF dạng `C` + 9 số từ sequence Oracle
3. Validate: khách hàng đủ 18 tuổi, CCCD 12 chữ số, SĐT Việt Nam 10 số
4. Trạng thái ban đầu `ACTIVE`
5. Phát event `CustomerCreated` (tạm thời gửi trực tiếp; sẽ chuyển sang outbox ở BANK-046)
6. Log và response API chỉ hiển thị CCCD dạng che: `0012******89`
7. Thêm `EventEnvelope.of(String eventType, String source, String traceId, T payload, Clock clock)` vào `common-events`: tự sinh `eventId` (UUID ngẫu nhiên) và `occurredAt` = `Instant.now(clock)`. Producer dùng method này thay vì tự gọi constructor. Lý do: đây là producer đầu tiên; nếu mỗi producer tự sinh `eventId`/`occurredAt` thì dễ quên `Clock` (convention mục 4.2) và khó test

## Acceptance criteria
- [ ] Tạo thành công → 201, trả về CIF
- [ ] Trùng CCCD → 409 `CUSTOMER_ID_NUMBER_DUPLICATED`; trùng SĐT → 409 `CUSTOMER_PHONE_DUPLICATED`
- [ ] Chưa đủ 18 tuổi → 400 `CUSTOMER_UNDERAGE`
- [ ] Role CUSTOMER gọi API → 403
- [ ] Integration test xác nhận event `CustomerCreated` xuất hiện trên topic (đọc bằng `KafkaTestConsumer` của `common-test`)
- [ ] Unit test `EventEnvelope.of` với `Clock.fixed(2026-11-21T10:00:00Z)` → `occurredAt` = `2026-11-21T10:00:00Z`; gọi 2 lần → 2 `eventId` khác nhau
- [ ] Lỗi validate của API → body theo `ErrorResponse` chung, `code` = `VALIDATION_FAILED` (handler của `common-core` tự bật)

## Ngoài phạm vi
- Lấy `traceId` tự động từ tracing (BANK-070); tạm thời truyền `traceId` từ MDC nếu có, không có thì `null`
- Tạo user Keycloak
- Upload ảnh CCCD, eKYC

## Phụ thuộc
- [BANK-003: Cấu hình datasource Oracle và Flyway cho các service](../E0-nen-tang/BANK-003-flyway-ket-noi-oracle.md)
- [BANK-004: common-core: định dạng lỗi chuẩn và mã lỗi](../E0-nen-tang/BANK-004-common-core-xu-ly-loi.md)
- [BANK-005: common-events: event envelope và cấu hình Kafka dùng chung](../E0-nen-tang/BANK-005-common-events-envelope.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md)
