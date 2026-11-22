---
name: lam-epic
description: Tự động thực hiện lần lượt tất cả task trong một epic theo thứ tự phụ thuộc, làm xong task nào đánh dấu DONE task đó. Ví dụ /lam-epic E0
disable-model-invocation: true
---

# Thực hiện toàn bộ epic $ARGUMENTS

Bạn là **người điều phối**. Bạn không tự code, mà giao từng task cho một subagent,
kiểm chứng kết quả, đánh dấu DONE rồi chuyển sang task tiếp theo cho đến khi hết epic.

Chế độ này chỉ có **một điểm dừng chờ duyệt** (Bước 1). Sau đó chạy liên tục,
trừ khi gặp một điều kiện dừng ở mục "Điều kiện dừng".

## Bước 1: Lập danh sách task ⛔ CHỜ DUYỆT
1. Tìm thư mục `backlog/$ARGUMENTS-*/`. Không có → báo lỗi và DỪNG.
2. Đọc front matter của mọi file `BANK-*.md` trong thư mục đó.
3. Kiểm tra trạng thái:
   - `DONE` → bỏ qua.
   - `IN_PROGRESS`, `REVIEW`, `BLOCKED` → báo cáo (có thể lần chạy trước bị dở) và DỪNG.
   - Task trong `depends_on` thuộc epic khác mà chưa `DONE` → báo cáo và DỪNG.
4. Sắp xếp các task `TODO`: task bị phụ thuộc làm trước, task phụ thuộc làm sau.
   Các task ngang hàng thì sắp theo ID tăng dần. Phát hiện phụ thuộc vòng → DỪNG.
5. Trình bày theo format rồi DỪNG chờ tôi trả lời "OK":

### Kế hoạch epic $ARGUMENTS
| Thứ tự | Task | Tiêu đề | Phụ thuộc |
**Bỏ qua (đã DONE):** ...

## Bước 2: Chuẩn bị
1. `git status` phải sạch. Không sạch → báo cáo và DỪNG.
2. Từ `main`, tạo branch `epic/$ARGUMENTS-<tên thư mục epic>`. Nếu branch đã tồn tại thì checkout và tiếp tục.
3. Tạo file `backlog/$ARGUMENTS-*/_tien-do.md` (nếu chưa có) với bảng:
   `| Task | Kết quả | Commit | Số test | Ghi chú |`

## Bước 3: Vòng lặp, lần lượt từng task theo thứ tự đã duyệt

### 3.1. Giao task cho subagent
Mỗi task dùng **một subagent mới** để có context sạch. Gửi subagent chỉ dẫn sau:

> Thực hiện task `<BANK-xxx>` theo `.claude/skills/lam-task/SKILL.md`, với các thay đổi:
> - Bỏ Bước 4.1 (không tạo branch). Đang ở branch epic, làm trực tiếp trên đó.
> - Bước 2, nếu có điểm mơ hồ: **không hỏi**, dừng lại và trả về `KẾT QUẢ: BLOCKED` kèm câu hỏi.
> - Bước 3: **không chờ duyệt plan**, đưa plan vào báo cáo cuối rồi làm tiếp.
> - Bước 6, nếu sau 3 vòng vẫn fail: trả về `KẾT QUẢ: FAILED` kèm lỗi.
> - Bước 8: không đổi status, không commit. Trả về báo cáo, dòng đầu là `KẾT QUẢ: SUCCESS`, `BLOCKED` hoặc `FAILED`.

### 3.2. Kiểm chứng (không tin báo cáo, tự kiểm tra lại)
1. Chạy `./mvnw clean verify` cho **toàn repo**, không chỉ module của task.
2. Xem `git status` / `git diff`: chỉ có các file nằm trong plan của task cùng file task đó.
3. Mọi acceptance criteria trong file task đều có test tương ứng trong báo cáo.

### 3.3. Ghi nhận kết quả
- **Đạt cả 3 mục ở 3.2:**
  1. Đổi `status` của task thành `DONE`.
  2. Commit toàn bộ thay đổi (code + file task) với message `feat(<BANK-xxx>): <mô tả>`.
  3. Thêm một dòng vào `_tien-do.md`, commit cùng lúc.
  4. Chuyển sang task tiếp theo.
- **Không đạt, hoặc subagent trả về BLOCKED/FAILED:**
  1. Đổi `status` thành `BLOCKED`, ghi lý do vào `_tien-do.md`.
  2. Commit riêng file task và `_tien-do.md` (`chore(<BANK-xxx>): blocked`), **không** commit code dở.
     Code dở để nguyên trong working tree để tôi xem.
  3. **DỪNG toàn bộ vòng lặp**, chuyển thẳng sang Bước 4.

## Điều kiện dừng
Dừng vòng lặp ngay (đánh dấu task hiện tại `BLOCKED`) khi:
- Subagent trả về `BLOCKED` hoặc `FAILED`.
- `./mvnw clean verify` toàn repo fail.
- Task cần thêm dependency ngoài danh sách công nghệ trong `docs/kien-truc.md`.
- Task cần migration có `DROP`, `TRUNCATE`, xóa cột hoặc xóa dữ liệu.
- Task buộc phải thay đổi hành vi code của một task đã `DONE` trước đó.

## Bước 4: Báo cáo cuối

### Kết quả epic $ARGUMENTS
**Branch:** `epic/...`
| Task | Kết quả | Commit | Số test |
**Task bị BLOCKED (nếu có):** lý do và câu hỏi cần tôi trả lời
**Quyết định đáng chú ý:** tổng hợp từ báo cáo của các subagent
**Đề xuất task mới:** tổng hợp từ báo cáo của các subagent
**Việc tôi cần làm:** review branch epic, rồi tự merge vào `main`

## Cấm tuyệt đối
- Không push, không merge vào `main`.
- Không bỏ qua task lỗi để làm tiếp task sau.
- Không đánh dấu `DONE` khi kiểm chứng ở 3.2 chưa đạt.
- Không sửa nội dung file task, trừ trường `status`.
- Không chạy lại một task đã `BLOCKED` trong cùng lần chạy.
