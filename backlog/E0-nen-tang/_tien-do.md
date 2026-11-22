# Tien do epic E0

| Task | Kết quả | Commit | Số test | Ghi chú |
|---|---|---|---|---|
| BANK-002 | DONE | feat(BANK-002) | 0 test Java (4 AC kiểm bằng lệnh compose/sqlplus/curl) + 7 IT cũ pass | Script init dạng .sh thay *.sql (mật khẩu từ env); thêm infra/.gitattributes |
| BANK-003 | DONE | feat(BANK-003) | 19 IT pass (6 service x 3 + gateway 1) | Thêm spring-boot-starter-flyway (Boot 4); IT dùng Testcontainers Oracle riêng từng service (chờ BANK-006 gom lại) |
