# Hạ tầng local

`docker-compose.yml` dựng toàn bộ hạ tầng cần cho các service khi chạy trên máy:

| Container | Image | Port trên máy | Ghi chú |
|---|---|---|---|
| `oracle` | `gvenzl/oracle-free:23.6-slim-faststart` | 1521 | PDB `FREEPDB1`, 6 schema của service |
| `kafka` | `apache/kafka:3.9.1` | 9092 | KRaft, 1 broker (broker + controller) |
| `kafka-ui` | `provectuslabs/kafka-ui:v0.7.2` | 8090 | Xem broker, topic, message |
| `redis` | `redis:7.4-alpine` | 6379 | Chỉ là cache/lock, không phải nguồn dữ liệu gốc |
| `keycloak` | `quay.io/keycloak/keycloak:26.6.4` | 8180 | Realm `bank` (OAuth2/JWT), tự import khi khởi động |

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

### Keycloak

- Admin console: <http://localhost:8180>, đăng nhập bằng `KEYCLOAK_ADMIN_USERNAME` / `KEYCLOAK_ADMIN_PASSWORD`
  trong `infra/.env` (realm `master`).
- Chạy chế độ `start-dev` (HTTP, dữ liệu Keycloak lưu trong container, chỉ dùng local).
- Khi khởi động, Keycloak import realm `bank` từ `infra/keycloak/realm-bank.json`. Realm đã tồn tại thì bỏ qua
  (không ghi đè). Sửa file realm → tạo lại container để import lại:
  `docker compose -f infra/docker-compose.yml up -d --wait --force-recreate keycloak`.
- Issuer của token: `http://localhost:8180/realms/bank`
  (discovery: <http://localhost:8180/realms/bank/.well-known/openid-configuration>).

Nội dung realm `bank`:

| Loại | Tên | Ghi chú |
|---|---|---|
| Realm role | `CUSTOMER` | Khách hàng |
| Realm role | `ADMIN` | Nhân viên vận hành |
| Client | `bank-web` | Public, chỉ bật password grant (Direct Access Grants) để lấy token khi test. Token có `aud` = `bank-gateway` và claim `cif` |
| Client | `bank-gateway` | Confidential, không bật flow nào: đại diện cho api-gateway (resource server), dùng làm audience của token |
| User | `customer01` | Role `CUSTOMER`, attribute `cif` = `C000000001`. Mật khẩu: `KEYCLOAK_CUSTOMER01_PASSWORD` |
| User | `admin01` | Role `ADMIN`, không có `cif`. Mật khẩu: `KEYCLOAK_ADMIN01_PASSWORD` |

Mật khẩu user mẫu không nằm trong file realm: file chứa placeholder `${KEYCLOAK_CUSTOMER01_PASSWORD}`,
`${KEYCLOAK_ADMIN01_PASSWORD}`, Keycloak thay bằng biến môi trường lúc import. Đổi mật khẩu trong `infra/.env`
chỉ có tác dụng khi realm được import lại (tạo lại container `keycloak`).

Lấy access token của `customer01` (chạy từ thư mục gốc của repo, Git Bash/Linux/macOS):

```bash
# Nạp mật khẩu từ infra/.env
set -a; . infra/.env; set +a

TOKEN=$(curl -s -X POST http://localhost:8180/realms/bank/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=bank-web \
  -d username=customer01 \
  --data-urlencode "password=${KEYCLOAK_CUSTOMER01_PASSWORD}" \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
```

Decode phần payload của token (base64url) để xem role và claim `cif`:

```bash
PAYLOAD=$(echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+')
while [ $(( ${#PAYLOAD} % 4 )) -ne 0 ]; do PAYLOAD="${PAYLOAD}="; done
echo "$PAYLOAD" | base64 -d; echo
```

Kết quả (rút gọn) có:

```json
{
  "iss": "http://localhost:8180/realms/bank",
  "aud": "bank-gateway",
  "azp": "bank-web",
  "realm_access": { "roles": ["CUSTOMER"] },
  "cif": "C000000001",
  "preferred_username": "customer01"
}
```

Token sống 5 phút (`accessTokenLifespan` = 300 giây). Lấy token của `admin01` tương tự với
`username=admin01` và `KEYCLOAK_ADMIN01_PASSWORD`: `realm_access.roles` là `["ADMIN"]`, không có claim `cif`.

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

## Integration test (Testcontainers)

Integration test (`*IT`) kế thừa `AbstractIntegrationTest` (module `common/common-test`) và tự khởi container
Oracle, Kafka, Redis riêng, **không** dùng các container của `docker-compose.yml`.

- Container Oracle chạy đúng script `infra/oracle/init/01_create_service_schemas.sh` ở trên, mật khẩu mọi schema là
  `test`. Thêm schema mới chỉ cần sửa mảng `SERVICE_SCHEMAS` trong script, không phải sửa `common-test`.
- Đường dẫn gốc repo truyền cho test qua system property `bank.test.repo-root` (failsafe tự đặt từ
  `${maven.multiModuleProjectDirectory}`). Chạy `*IT` từ IDE không qua Maven thì thêm VM option
  `-Dbank.test.repo-root=<đường dẫn gốc repo>`.

### Bật reuse container trên máy dev

Mặc định mỗi lần chạy `verify`, mỗi module khởi container mới (~45 giây cho Oracle). Trên máy dev có thể giữ
container sống giữa các lần chạy bằng cách thêm dòng sau vào `~/.testcontainers.properties`
(Windows: `C:\Users\<user>\.testcontainers.properties`; tạo file nếu chưa có):

```properties
testcontainers.reuse.enable=true
```

- Lần chạy sau dùng lại container đang chạy có cùng cấu hình (log Testcontainers báo `Reusing container`).
  Sửa script init Oracle hoặc image → cấu hình đổi → Testcontainers tạo container mới.
- Dữ liệu của lần chạy trước (bảng, topic, key Redis) vẫn còn: test phải tự tạo dữ liệu riêng, không giả định DB trống.
- Container reuse **không** tự bị xóa khi test xong (Ryuk bỏ qua container reuse). Dọn bằng:

  ```bash
  docker rm -f $(docker ps -aq --filter label=org.testcontainers.hash)
  ```

- CI không bật cờ này nên CI luôn dùng container mới mỗi lần chạy.

## Xử lý sự cố

| Hiện tượng | Nguyên nhân thường gặp |
|---|---|
| `ORACLE_PASSWORD is required` khi `up` | Chưa tạo `infra/.env` từ `infra/.env.example` |
| Oracle không lên `healthy` | Docker thiếu RAM; xem `docker compose -f infra/docker-compose.yml logs oracle` |
| `bind: address already in use` | Port 1521/9092/6379/8090/8180 đang bị chương trình khác dùng |
| Lấy token báo `invalid_grant` | Sai mật khẩu, hoặc đã đổi mật khẩu trong `infra/.env` nhưng chưa tạo lại container `keycloak` |
| Script init báo `$'\r': command not found` | File `.sh` bị đổi sang CRLF; `infra/.gitattributes` ép LF, checkout lại file |
