# Tiến độ epic E2

| Task | Kết quả | Commit | Số test | Ghi chú |
|---|---|---|---|---|
| BANK-020 | DONE | feat(BANK-020) | +28, toàn repo 225 pass | Gọi customer-service ngoài transaction (404→CUSTOMER_NOT_FOUND, lỗi khác→503 CUSTOMER_SERVICE_UNAVAILABLE); giới hạn 3 đếm bằng count, còn race khi đồng thời; CK_ACCOUNT_BALANCE/TYPE cần nới ở BANK-030; payload AccountOpened thêm vào common-events; chép RequiresRole từ customer-service |
| BANK-021 | DONE | feat(BANK-021) | +26, toàn repo 251 pass | Cache-aside `balance:{accountNo}` TTL 5m, lưu cả cif để kiểm chủ; Redis lỗi → WARN + đọc DB; `BalanceCacheEvictor.evictAfterCommit` để BANK-023/031/032 gọi; chỉ CUSTOMER xem (ADMIN 403); tài khoản không tồn tại → 404 ACCOUNT_NOT_FOUND |
| BANK-022 | DONE | feat(BANK-022) | +42, toàn repo 293 pass | API lock/unlock/close (ADMIN, reason bắt buộc), V3 ACCOUNT_STATUS_HISTORY; đổi sang cùng trạng thái → 422; consumer CustomerStatusChanged (consumer đầu tiên) chỉ khóa khi khách LOCKED, không tự mở khóa; idempotent qua PROCESSED_MESSAGE trong cùng transaction; auto-offset-reset earliest; giới hạn 3 tài khoản vẫn đếm cả CLOSED |
