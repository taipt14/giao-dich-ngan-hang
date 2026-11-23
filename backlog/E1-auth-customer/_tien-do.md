# Tien do epic E1

| Task | Kết quả | Commit | Số test | Ghi chú |
|---|---|---|---|---|
| BANK-010 | DONE | feat(BANK-010) | 0 test Java (AC kiểm bằng curl lấy token + decode), toàn repo 121 pass | Keycloak 26.6.4 start-dev, H2 nội bộ; mật khẩu user mẫu là placeholder từ env; customer01 cif=C000000001; issuer theo host gọi (lưu ý cho BANK-011/076) |
| BANK-011 | DONE | feat(BANK-011) | +11 IT, +1 unit ở api-gateway; toàn repo 132 pass | Kiểm aud=bank-gateway; issuer-uri và jwk-set-uri tách riêng; header X-* client gửi bị xóa và đặt lại từ JWT; lỗi downstream/timeout → 503; subagent down -v đã xóa volume oracle-data local |
| BANK-012 | DONE | feat(BANK-012) | +8 IT ở api-gateway; toàn repo 140 pass | Filter RequestRateLimiter + limiter cửa sổ trượt tự viết (Lua, sorted set) thay token bucket của framework để đạt AC; key ratelimit:{routeId}:{userId}, đếm riêng từng route; Redis lỗi → fail-open + WARN |
