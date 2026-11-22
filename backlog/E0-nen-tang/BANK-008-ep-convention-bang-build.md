---
id: BANK-008
epic: E0
title: Ép convention bằng build: Spotless, Checkstyle, ArchUnit, maven-enforcer
priority: High
depends_on: [BANK-001]
status: TODO
---

# BANK-008: Ép convention bằng build: Spotless, Checkstyle, ArchUnit, maven-enforcer

## Bối cảnh nghiệp vụ
Mục 20 `convention-code.md` ghi rằng format, import, kiến trúc tầng "được ép bằng build", nhưng hiện chưa có công cụ nào được cấu hình. Code của agent chỉ được review bằng mắt. Phải có trước E1, vì code nghiệp vụ đầu tiên (BANK-013) sẽ trở thành code mẫu (mục 21 convention): nếu code mẫu sai chuẩn thì các task sau chép theo cái sai.

## Phạm vi kỹ thuật
- `pom.xml` gốc: thêm plugin và version vào `pluginManagement` / `dependencyManagement`
- File cấu hình Checkstyle: `config/checkstyle/checkstyle.xml`
- Module `common/common-test`: class chứa các rule ArchUnit dùng chung
- Mỗi service trong `services/`: một test `ArchitectureTest` gọi rule dùng chung
- Code hiện có (BANK-001 → BANK-007): chỉ format lại và sửa vi phạm, **không** đổi hành vi

## Yêu cầu chi tiết
1. **Spotless** (`palantir-java-format`, `removeUnusedImports`): goal `check` chạy trong `./mvnw verify` ở mọi module. Chạy `./mvnw spotless:apply` một lần cho toàn bộ code hiện có.
2. **Checkstyle** chạy trong `verify`, vi phạm → build fail (`failOnViolation=true`, severity `error`). Chỉ kiểm `src/main/java`. Các rule:
   - `AvoidStarImport`: không wildcard import
   - `RegexpSinglelineJava`: cấm `System.out`, `System.err`, `printStackTrace(`
   - `MatchXpath`: cấm `@Autowired` trên field (`//VARIABLE_DEF[./MODIFIERS/ANNOTATION/IDENT[@text='Autowired']]`)
   - `MethodLength` max 30 dòng (`countEmpty=false`), `FileLength` max 300 dòng
3. **ArchUnit**: các rule đặt trong một class `ArchitectureRules` của `common-test` (package `vn.bank.common.test.arch`). Mỗi service có `ArchitectureTest` (hậu tố `*Test`, chạy bằng surefire) dùng `@ArchTest` gọi lại các rule đó với package `vn.bank.<service>`. Các rule:
   - Phụ thuộc giữa các tầng đúng bảng mục 2 `convention-code.md`, gồm cả ngoại lệ consumer/job (`infrastructure.messaging` và class `*Job` được gọi `application`)
   - Class trong `..api..` không phụ thuộc `..infrastructure..`
   - Class trong `..domain..` không phụ thuộc `org.springframework..` (annotation `jakarta.persistence` được phép)
   - `@Transactional` chỉ xuất hiện trên class/method trong `..application..`
   - Không class nào có field kiểu `double`, `float`, `Double`, `Float`. Lý do: không phân biệt được field nào là tiền, mà project không có nhu cầu số thực nhị phân.
   - Class có `@Entity` không có method public tên bắt đầu bằng `set`. Lý do: `@Data`/`@Setter` của Lombok có retention `SOURCE` nên ArchUnit không thấy annotation, chỉ thấy setter đã sinh ra.
   - Rule phải dùng `allowEmptyShould(true)`, vì hiện các service chưa có package `api`/`domain`... (rule rỗng mặc định sẽ fail).
4. **maven-enforcer** chạy ở phase `validate`: `requireJavaVersion` `[21,)`, `requireMavenVersion` `[3.9,)`, `banDuplicatePomDependencyVersions`.
5. Version plugin và `archunit-junit5` khai báo một lần ở `pom.xml` gốc. POM con không ghi version (giữ AC của BANK-001).
6. Cập nhật mục 20 `convention-code.md` nếu có rule nào được ép bằng cách khác với mô tả hiện tại (ví dụ setter thay cho annotation Lombok).

## Acceptance criteria
- [ ] Code hiện có sau khi `spotless:apply` và sửa vi phạm → `./mvnw clean verify` pass, toàn bộ test cũ vẫn pass
- [ ] File Java format sai → `./mvnw verify` fail, thông báo gợi ý `spotless:apply`
- [ ] Class trong `src/main/java` có wildcard import / `System.out` / `@Autowired` trên field → `./mvnw verify` fail ở Checkstyle (kiểm bằng file tạm, xóa sau khi kiểm; ghi lệnh và kết quả vào báo cáo)
- [ ] Unit test trong `common-test` chạy từng rule ArchUnit với class mẫu vi phạm (đặt trong `src/test`) → rule báo vi phạm; class mẫu đúng → rule pass. Mỗi rule ở yêu cầu 3 có ít nhất một cặp test như vậy
- [ ] Chạy build bằng JDK 17 → fail ở enforcer với thông báo yêu cầu Java 21

## Ngoài phạm vi
- Đo coverage (JaCoCo), SonarQube
- Sửa workflow CI: CI đã chạy `./mvnw clean verify` (BANK-007) nên tự ép các rule mới
- Đổi logic code hiện có. Nếu phải tách method dài hơn 30 dòng thì chỉ tách, không đổi hành vi

## Ghi chú cho agent
- Rule nào không ép được đúng như mô tả ở trên hoặc ở mục 20 `convention-code.md` → **dừng và hỏi**, không tự nới rule.
- Code hiện có vi phạm theo cách không sửa được mà không đổi hành vi → hỏi lại, không thêm suppress.

## Phụ thuộc
- [BANK-001: Khởi tạo Maven multi-module và parent POM](BANK-001-khoi-tao-maven-multi-module.md)

## Tài liệu tham khảo
- [Kiến trúc](../../docs/kien-truc.md)
- [Convention viết code](../../docs/convention-code.md), mục 2, 15, 20
