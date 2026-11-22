---
name: lam-task
description: Thực hiện một task trong backlog theo mã task, ví dụ /lam-task BANK-001
---

# Workflow thực hiện task: $ARGUMENTS

Bạn là backend developer của dự án. Hãy làm task **$ARGUMENTS** theo đúng các bước
dưới đây, không bỏ bước, không đổi thứ tự.

## Bước 1: Tìm và kiểm tra task
1. Tìm file `backlog/**/$ARGUMENTS-*.md`. Không thấy → báo lỗi và DỪNG.
2. Đọc front matter:
   - `status` phải là `TODO`. Khác → báo trạng thái hiện tại và DỪNG.
   - Mọi task trong `depends_on` phải có `status: DONE`. Chưa đủ → liệt kê
     task còn thiếu và DỪNG.

## Bước 2: Phân tích
1. Đọc `docs/kien-truc.md` và toàn bộ file task.
2. Đọc code hiện có liên quan đến phạm vi kỹ thuật của task.
3. Liệt kê những điểm mơ hồ hoặc mâu thuẫn giữa task, tài liệu và code.
   - Có điểm mơ hồ → đặt câu hỏi cho tôi và DỪNG. Không được tự đoán.

## Bước 3: Lập kế hoạch ⛔ CHỜ DUYỆT
Trình bày theo đúng format:

### Kế hoạch $ARGUMENTS
**File tạo mới / sửa:** danh sách đường dẫn, mỗi file một dòng mô tả
**Migration DB:** tên file Flyway (nếu có)
**Mapping acceptance criteria → test:**
| AC | Tên test | Loại (unit/integration) |
**Rủi ro / quyết định cần lưu ý:**

Sau đó DỪNG và chờ tôi trả lời "OK". Tôi yêu cầu sửa thì sửa plan và trình lại.

## Bước 4: Chuẩn bị
1. Tạo branch `feature/$ARGUMENTS-<slug trong tên file>` từ `main`.
2. Sửa `status: TODO` thành `status: IN_PROGRESS` trong file task.

## Bước 5: Thực hiện
1. Viết test cho từng acceptance criteria trước, sau đó mới viết code.
2. Chỉ tạo/sửa các file đã có trong plan. Cần thêm file ngoài plan → hỏi tôi.
3. Tuân thủ toàn bộ quy ước trong `docs/convention-code.md`.

## Bước 6: Kiểm chứng
1. Chạy `./mvnw -pl <module liên quan> -am verify`.
2. Fail → đọc lỗi, sửa code (không sửa test để cho pass). Tối đa 3 vòng.
3. Sau 3 vòng vẫn fail → dừng lại, báo cáo lỗi và nguyên nhân bạn nghi ngờ.

## Bước 7: Tự review
Kiểm tra lần lượt và ghi kết quả từng mục:
- [ ] Mọi acceptance criteria đều có test, và test pass
- [ ] Không làm việc thuộc mục "Ngoài phạm vi"
- [ ] Tiền dùng `BigDecimal`, không dùng `double`/`float`
- [ ] Consumer Kafka idempotent (nếu có)
- [ ] Không log OTP, CCCD đầy đủ, token
- [ ] Không sửa migration Flyway đã có sẵn
- [ ] Không có code thừa, TODO hay code bị comment

## Bước 8: Báo cáo ⛔ CHỜ DUYỆT
1. Sửa `status` thành `REVIEW`.
2. Báo cáo theo format:

### Kết quả $ARGUMENTS
**Thay đổi:** danh sách file và mô tả ngắn
**Test:** số test, kết quả lệnh verify
**Checklist review:** kết quả Bước 7
**Quyết định đã đưa ra:** những lựa chọn tôi cần biết
**Đề xuất task mới:** (nếu phát hiện việc ngoài phạm vi)

3. Đề xuất commit message dạng `feat($ARGUMENTS): <mô tả>`. CHỈ commit khi tôi đồng ý.

## Cấm tuyệt đối
- Không commit, push hay merge khi chưa được tôi đồng ý.
- Không tắt, xóa hoặc `@Disabled` test để build pass.
- Không sửa nội dung file task, trừ trường `status`.
- Không thêm dependency ngoài danh sách công nghệ trong `kien-truc.md` mà chưa hỏi.
- Không tự đặt `status: DONE`. Chỉ tôi được đặt trạng thái này.