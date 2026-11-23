# Tiến độ epic E2

| Task | Kết quả | Commit | Số test | Ghi chú |
|---|---|---|---|---|
| BANK-020 | DONE | feat(BANK-020) | +28, toàn repo 225 pass | Gọi customer-service ngoài transaction (404→CUSTOMER_NOT_FOUND, lỗi khác→503 CUSTOMER_SERVICE_UNAVAILABLE); giới hạn 3 đếm bằng count, còn race khi đồng thời; CK_ACCOUNT_BALANCE/TYPE cần nới ở BANK-030; payload AccountOpened thêm vào common-events; chép RequiresRole từ customer-service |
