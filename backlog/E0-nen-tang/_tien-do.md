# Tien do epic E0

| Task | Kết quả | Commit | Số test | Ghi chú |
|---|---|---|---|---|
| BANK-002 | DONE | feat(BANK-002) | 0 test Java (4 AC kiểm bằng lệnh compose/sqlplus/curl) + 7 IT cũ pass | Script init dạng .sh thay *.sql (mật khẩu từ env); thêm infra/.gitattributes |
| BANK-003 | DONE | feat(BANK-003) | 19 IT pass (6 service x 3 + gateway 1) | Thêm spring-boot-starter-flyway (Boot 4); IT dùng Testcontainers Oracle riêng từng service (chờ BANK-006 gom lại) |
| BANK-004 | DONE | feat(BANK-004) | 16 mới (13 unit + 3 IT), toàn repo 35 pass | Auto-config chỉ cho SERVLET; spring-webmvc optional; chưa thêm common-core vào service |
| BANK-005 | DONE | feat(BANK-005) | 11 unit mới, toàn repo 46 pass | Jackson 3 + Spring Kafka 4; JsonMapper riêng cho event; acks/idempotence là mặc định ghi đè được; traceId cho phép null |
| BANK-006 | DONE | feat(BANK-006) | 6 IT mới ở account-service, toàn repo 52 pass | Container singleton static (Ryuk dọn); @DynamicPropertySource giữ username từ application.yml; 5 service khác chưa chuyển sang base class |
| BANK-007 | DONE | ci(BANK-007), fix(BANK-007) | CI GitHub: xanh trên PR #1 và run 45050152852; đỏ khi có test fail (run 45050014019) | mvnw đặt mode 100755, bỏ bước chmod trong workflow |
| BANK-008 | DONE | feat(BANK-008) | 63 mới (21 rule test + 7x6 ArchitectureTest), toàn repo 115 pass | .gitattributes *.java eol=lf; thêm spring-tx, jakarta.persistence-api (test) cho common-test; đã thêm common-test vào 6 service; rule setter chặn mọi tên bắt đầu bằng set |
