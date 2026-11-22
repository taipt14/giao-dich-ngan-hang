# Hạ tầng local

`docker-compose.yml` dựng toàn bộ hạ tầng cần cho các service khi chạy trên máy:

| Container | Image | Port trên máy | Ghi chú |
|---|---|---|---|
| `oracle` | `gvenzl/oracle-free:23.6-slim-faststart` | 1521 | PDB `FREEPDB1`, 6 schema của service |
| `kafka` | `apache/kafka:3.9.1` | 9092 | KRaft, 1 broker (broker + controller) |
| `kafka-ui` | `provectuslabs/kafka-ui:v0.7.2` | 8090 | Xem broker, topic, message |
| `redis` | `redis:7.4-alpine` | 6379 | Chỉ là cache/lock, không phải nguồn dữ liệu gốc |

Yêu cầu: Docker Desktop (hoặc Docker Engine) có Compose v2, cấp cho Docker tối thiểu 4 GB RAM (Oracle cần ~2 GB).

## Khởi động

Chạy từ thư mục gốc của repo:

```bash
# 1. Lần đầu: tạo file biến môi trường rồi sửa mật khẩu nếu muốn
cp infra/.env.example infra/.env

# 2. Khởi động và chờ mọi container healthy
docker compose -f infra/docker-compose.yml up -d --wait

# 3. Kiểm tra trạng thái (cột STATUS phải là "healthy")
docker compose -f infra/docker-compose.yml ps
```

Lần đầu khởi động, Oracle chạy script `infra/oracle/init/01_create_service_schemas.sh`
để tạo user/schema cho từng service. Oracle chỉ báo `healthy` sau khi script chạy xong.

Dừng (giữ nguyên dữ liệu):

```bash
docker compose -f infra/docker-compose.yml down
```

## Kết nối

### Oracle

| Schema (user) | Service | Biến mật khẩu trong `infra/.env` |
|---|---|---|
| `CUSTOMER_SVC` | customer-service | `CUSTOMER_SVC_DB_PASSWORD` |
| `ACCOUNT_SVC` | account-service | `ACCOUNT_SVC_DB_PASSWORD` |
| `TRANSFER_SVC` | transfer-service | `TRANSFER_SVC_DB_PASSWORD` |
| `NAPAS_SVC` | mock-napas-service | `NAPAS_SVC_DB_PASSWORD` |
| `NOTI_SVC` | notification-service | `NOTI_SVC_DB_PASSWORD` |
| `FRAUD_SVC` | fraud-service | `FRAUD_SVC_DB_PASSWORD` |

- JDBC URL: `jdbc:oracle:thin:@localhost:1521/FREEPDB1`
- User `SYS`/`SYSTEM`: mật khẩu là `ORACLE_PASSWORD`.
- Mật khẩu chỉ nên gồm chữ cái và chữ số (ký tự `/`, `@`, `"` làm hỏng chuỗi kết nối và script init).

Kiểm tra đăng nhập bằng một schema (ví dụ `ACCOUNT_SVC`) từ trong container:

```bash
docker compose -f infra/docker-compose.yml exec oracle bash -c \
  'echo "select user from dual;" | sqlplus -s -L "ACCOUNT_SVC/${ACCOUNT_SVC_DB_PASSWORD}@localhost:1521/FREEPDB1"'
```

### Kafka

- Service chạy trên máy: `bootstrap-servers=localhost:9092`
- Container trong cùng compose: `kafka:29092`
- Kafka UI: <http://localhost:8090> → cluster `local`, mục **Brokers** hiển thị broker `1`.

### Redis

- `localhost:6379`, không đặt mật khẩu (chỉ dùng local).

```bash
docker compose -f infra/docker-compose.yml exec redis redis-cli ping
```

## Reset dữ liệu

Xóa toàn bộ dữ liệu Oracle và Kafka (volume `oracle-data`, `kafka-data` của project này) rồi khởi động lại từ đầu.
Script init Oracle sẽ chạy lại và tạo lại các schema:

```bash
docker compose -f infra/docker-compose.yml down -v
docker compose -f infra/docker-compose.yml up -d --wait
```

Chỉ reset một phần:

```bash
# Chỉ xóa dữ liệu Redis
docker compose -f infra/docker-compose.yml exec redis redis-cli FLUSHALL

# Chỉ xóa dữ liệu Kafka
docker compose -f infra/docker-compose.yml rm -sf kafka-ui kafka
docker volume rm giao-dich-ngan-hang_kafka-data
docker compose -f infra/docker-compose.yml up -d --wait
```

Đổi mật khẩu trong `infra/.env` **không** có tác dụng với schema đã tạo (script init chỉ chạy khi volume
Oracle còn trống) → cần reset dữ liệu Oracle để áp dụng mật khẩu mới.

## Xử lý sự cố

| Hiện tượng | Nguyên nhân thường gặp |
|---|---|
| `ORACLE_PASSWORD is required` khi `up` | Chưa tạo `infra/.env` từ `infra/.env.example` |
| Oracle không lên `healthy` | Docker thiếu RAM; xem `docker compose -f infra/docker-compose.yml logs oracle` |
| `bind: address already in use` | Port 1521/9092/6379/8090 đang bị chương trình khác dùng |
| Script init báo `$'\r': command not found` | File `.sh` bị đổi sang CRLF; `infra/.gitattributes` ép LF, checkout lại file |
