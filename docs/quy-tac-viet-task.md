# Quy tắc viết file markdown cho AI agent

Mục tiêu: agent đọc task một lần là hiểu phải làm gì, làm đến đâu thì dừng, và tự kiểm chứng được kết quả, không phải đoán.

---

## A. Cấu trúc

### 1. Mọi task dùng chung một bộ heading, theo cùng thứ tự
Agent sẽ học được rằng "điều kiện hoàn thành luôn nằm ở `## Acceptance criteria`". Dùng [mau-task.md](mau-task.md), không tự thêm hoặc đổi tên heading.

### 2. Thông tin máy đọc được để trong front matter
`id`, `epic`, `depends_on`, `status`, `priority` để ở khối `---` đầu file. Agent (hoặc script import Jira) đọc các trường này mà không cần hiểu nội dung bên dưới.

### 3. Không lặp lại context chung, hãy dẫn link
Quy ước chung (dùng `BigDecimal`, consumer idempotent, format lỗi...) chỉ ghi **một lần** trong `docs/kien-truc.md`. Task chỉ ghi những gì riêng của task đó và link tới tài liệu chung.
Lý do: nội dung bị lặp ở nhiều nơi sớm muộn sẽ lệch nhau, và khi đó agent không biết tin bản nào.

### 4. Một task chỉ có một kết quả, và đủ nhỏ
- Làm xong trong khoảng 0,5–2 ngày (nếu người làm), thay đổi dưới ~500 dòng code
- Tiêu đề có "và" nối hai việc lớn thì thường nên tách làm hai task
- Task quá lớn khiến agent mất phương hướng, còn bạn thì khó review

---

## B. Nội dung

### 5. Viết cụ thể, không dùng từ cảm tính
| ❌ Mơ hồ | ✅ Cụ thể |
|---|---|
| Xử lý nhanh | p95 < 300ms |
| Kiểm tra hạn mức hợp lý | Tối đa 100.000.000/lần, 500.000.000/ngày |
| Báo lỗi nếu không đủ tiền | 422, code `INSUFFICIENT_BALANCE` |
| Lưu thông tin giao dịch | Bảng `TRANSFER`, các cột: ... |
| Đảm bảo an toàn | Khóa `SELECT ... FOR UPDATE`, thứ tự khóa theo `ACCOUNT_NO` |

Có số thì ghi số, có tên (bảng, class, topic, mã lỗi) thì ghi tên.

### 6. Mỗi acceptance criteria phải chuyển được thành một test
Viết theo dạng **điều kiện → kết quả mong đợi**:
```markdown
- [ ] Gửi lại cùng Idempotency-Key → không tạo lệnh mới, trả về lệnh cũ
- [ ] 20 thread cùng trừ 100.000 từ tài khoản 1.000.000 → 10 thành công, số dư cuối = 0
```
Nếu không biết test một dòng AC như thế nào thì dòng đó đang viết chưa đủ rõ.

### 7. Luôn có mục "Ngoài phạm vi"
AI có xu hướng làm thêm những việc "tiện tay". Ghi rõ những gì không làm, và task nào sẽ làm việc đó:
```markdown
## Ngoài phạm vi
- Tính phí (BANK-044)
- Gửi SMS thật
```

### 8. Giải thích lý do cho các quy tắc quan trọng
```markdown
❌ Không dùng cache số dư khi trừ tiền.
✅ Không dùng cache số dư khi trừ tiền, vì cache có thể cũ và dẫn đến số dư âm.
   Cache chỉ dùng để hiển thị.
```
Khi hiểu mục đích, agent xử lý được cả những tình huống bạn chưa nghĩ tới.

### 9. Cho ví dụ input/output thật
Một ví dụ JSON request/response hoặc một câu thông báo mẫu thường rõ hơn ba đoạn mô tả.

### 10. Chỉ rõ chỗ phải dừng lại để hỏi
```markdown
## Ghi chú cho agent
Nếu thiết kế timeout có điểm chưa rõ, PHẢI hỏi lại trước khi code.
```
Đặc biệt với những chỗ liên quan đến tiền, bảo mật hoặc thay đổi khó hoàn tác (migration, xóa dữ liệu).

---

## C. Ngôn ngữ và định dạng

### 11. Thống nhất thuật ngữ
Một khái niệm chỉ dùng một từ trong toàn bộ tài liệu. Nếu đã viết "lệnh chuyển khoản" thì không lúc gọi "giao dịch", lúc gọi "transfer", lúc gọi "order". Nếu cần, thêm bảng thuật ngữ vào `kien-truc.md`.

### 12. Định danh kỹ thuật giữ nguyên tiếng Anh và đặt trong backtick
Viết mô tả bằng tiếng Việt cũng được, nhưng tên class, bảng, cột, topic, mã lỗi giữ đúng như trong code: `TransferCompleted`, `LEDGER_ENTRY`, `INSUFFICIENT_BALANCE`. Agent sẽ tìm kiếm trong code bằng đúng các chuỗi này.

### 13. Ưu tiên danh sách và bảng, hạn chế đoạn văn dài
- Danh sách đánh số khi các bước có thứ tự
- Bảng khi dữ liệu có nhiều cột (cấu hình, mapping, biểu phí)
- Code block luôn ghi ngôn ngữ: ` ```json `, ` ```sql `, ` ```java `

### 14. Sơ đồ dùng Mermaid, không dùng ảnh
Mermaid là text nên agent đọc được, sửa được và diff được trong git. Nếu bắt buộc dùng ảnh thì kèm mô tả bằng chữ.

### 15. Dùng "phải / không được" rõ ràng, chữ in đậm dùng tiết kiệm
In đậm chỉ dành cho những điều cấm hoặc bắt buộc quan trọng nhất. Nếu cái gì cũng in đậm thì không còn gì nổi bật.

---

## D. Bảo trì

### 16. Tài liệu đổi cùng lúc với quyết định
Khi đổi thiết kế, sửa `kien-truc.md` và các task liên quan **trước** khi giao việc cho agent. Agent tin tuyệt đối vào tài liệu, nên tài liệu sai thì code cũng sai.

### 17. Task đã xong thì không sửa nội dung, chỉ cập nhật `status`
Phát sinh yêu cầu mới thì tạo task mới. Như vậy lịch sử còn nguyên và agent không làm lại việc cũ.

---

## Checklist trước khi giao task cho agent

- [ ] Front matter đủ `id`, `epic`, `depends_on`, `status`
- [ ] Các task trong `depends_on` đã `DONE`
- [ ] Bối cảnh nghiệp vụ có số liệu và quy tắc cụ thể
- [ ] Có tên bảng, service, topic, mã lỗi
- [ ] Mỗi acceptance criteria viết được thành test
- [ ] Có mục "Ngoài phạm vi"
- [ ] Không còn từ mơ hồ ("hợp lý", "nhanh", "an toàn", "tối ưu"...)
- [ ] Không lặp lại quy ước đã có trong `kien-truc.md`
- [ ] Đã đánh dấu những chỗ agent phải hỏi lại
