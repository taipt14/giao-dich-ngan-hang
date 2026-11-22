---
id: BANK-009
epic: E0
title: Chuyển integration test của các service sang AbstractIntegrationTest
priority: High
depends_on: [BANK-006, BANK-008]
status: TODO
---

# BANK-009: Chuyển integration test của các service sang AbstractIntegrationTest

## Bối cảnh nghiệp vụ
BANK-003 viết sẵn một khối khởi động container Oracle trong từng `*ApplicationIT` của 6 service có DB. BANK-006 sau đó mới tạo `AbstractIntegrationTest` (container singleton), và chỉ test mẫu ở `account-service` dùng nó. Hệ quả:
- Cùng một đoạn setup container bị chép 6 lần.
- `account-service` khởi 2 container Oracle (~45 giây mỗi cái).
- Danh sách 6 schema và quyền GRANT bị lặp giữa `infra/oracle/init/01_create_service_schemas.sh` và `AbstractIntegrationTest`, nên thêm schema phải sửa ở hai nơi.

Từ E1 mọi service sẽ viết thêm `*IT`. Phải có một cách dùng chung trước khi số test tăng lên.

Lưu ý: failsafe chạy mỗi module trong một JVM riêng, nên mỗi module vẫn khởi container của riêng nó. Task này không làm build CI nhanh hơn nhiều. Tốc độ chỉ cải thiện thật ở máy dev, nhờ cơ chế reuse ở yêu cầu 4.

## Phạm vi kỹ thuật
- `services/{customer,account,transfer,mock-napas,notification,fraud}-service/src/test/`
- `services/*/pom.xml`: thêm `common-test` scope test (5 service chưa có)
- Module `common/common-test`
- `pom.xml` gốc: cấu hình failsafe truyền đường dẫn gốc repo (xem yêu cầu 3)
- `infra/README.md`: hướng dẫn bật reuse

## Yêu cầu chi tiết
1. 6 `*ApplicationIT` kế thừa `AbstractIntegrationTest`, xóa khối `@Testcontainers` / `@Container` / `@DynamicPropertySource` riêng. Giữ nguyên tên và ý nghĩa 3 test hiện có:
   - `should_startAndReportHealthUp_when_applicationLaunched`
   - `should_createFlywayHistoryInOwnSchema_when_serviceStarted`
   - `should_connectAsOwnSchemaUser_when_serviceStarted`: vẫn assert `select user from dual` = schema của service. Lý do: base class có đủ 6 user nên service cấu hình nhầm user vẫn kết nối được, chỉ assert này bắt được lỗi.
2. `account-service` chỉ còn một container Oracle. Gộp hoặc giữ `AccountServiceIntegrationSampleIT` / `SharedContainersReuseIT` của BANK-006 sao cho AC của BANK-006 vẫn có test.
3. Nguồn duy nhất cho danh sách schema là `infra/oracle/init/01_create_service_schemas.sh`:
   - `AbstractIntegrationTest` copy script đó vào `/container-entrypoint-initdb.d` và đặt các biến `<SCHEMA>_DB_PASSWORD` cho container test.
   - Đường dẫn gốc repo truyền qua system property của failsafe (`${maven.multiModuleProjectDirectory}`), không hard-code đường dẫn tương đối.
   - Xóa script SQL sinh trong Java của BANK-006.
4. Container trong `AbstractIntegrationTest` bật `.withReuse(true)`. Reuse chỉ có tác dụng khi máy dev tự bật `testcontainers.reuse.enable=true` trong `~/.testcontainers.properties`. CI không bật, nên CI vẫn dùng container mới mỗi lần chạy. Ghi cách bật vào `infra/README.md`.

## Acceptance criteria
- [ ] `./mvnw clean verify` → pass. Mỗi module service khởi đúng 1 container Oracle (đếm từ log Testcontainers, ghi số liệu vào báo cáo)
- [ ] Không còn `@Container` / `OracleContainer` nào trong `services/*/src/test`
- [ ] Đổi `spring.datasource.username` của một service sang schema khác (thử tạm) → `should_connectAsOwnSchemaUser_when_serviceStarted` fail; trả lại cấu hình → pass
- [ ] Thêm tạm một schema vào script `infra/oracle/init` → container test có user đó mà không phải sửa `common-test` (kiểm bằng test hoặc lệnh, rồi hoàn tác)
- [ ] Bật `testcontainers.reuse.enable=true`, chạy `./mvnw -pl services/account-service -am verify` 2 lần → lần 2 dùng lại container Oracle của lần 1 (cùng container id)

## Ngoài phạm vi
- Chỉ khởi những container mà service thật sự dùng (ví dụ không khởi Kafka cho service chưa dùng Kafka). Ghi thành đề xuất nếu thấy cần.
- Đổi nội dung `infra/docker-compose.yml`
- Test nghiệp vụ

## Ghi chú cho agent
Nếu yêu cầu 3 buộc phải đổi hành vi script init của BANK-002 (ví dụ đổi cách đọc mật khẩu) → dừng và hỏi.

## Phụ thuộc
- [BANK-006: common-test: base class Testcontainers dùng chung](BANK-006-common-test-testcontainers.md)
- [BANK-008: Ép convention bằng build](BANK-008-ep-convention-bang-build.md): code test mới cũng phải qua Spotless

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md), mục 18
