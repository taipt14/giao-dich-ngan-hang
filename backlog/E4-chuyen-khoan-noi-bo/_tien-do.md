# Tiến độ epic E4

| Task | Kết quả | Commit | Số test | Ghi chú |
|---|---|---|---|---|
| BANK-040 | DONE | (commit này) | +132, toàn repo 562 | Bảng TRANSFER + TRANSFER_STATUS_HISTORY (V2), TransferStatus 8 trạng thái, lịch sử ghi bằng cascade từ entity, UK (CIF, IDEMPOTENCY_KEY) tạo sẵn, sơ đồ docs/transfer-state.md |
| BANK-041 | DONE | (commit này) | +54, toàn repo 616 | POST /api/v1/transfers (CUSTOMER), idempotency 2 lớp Redis SET NX `idem:{cif}:{key}` 24h + UK DB; thêm GET /internal/accounts/{accountNo} ở account-service (chỉ thêm mới); phí dự kiến tạm = 0 chờ BANK-044; mã lỗi thêm IDEMPOTENCY_KEY_IN_PROGRESS 409, IDEMPOTENCY_KEY_INVALID 400 |
