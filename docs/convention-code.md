# Convention viết code

> File này **bắt buộc** với mọi task. Agent đọc file này cùng với [kien-truc.md](kien-truc.md) trước khi code.
> Nếu task yêu cầu khác với file này → **hỏi lại**, không tự chọn.
> Những gì công cụ kiểm tra được (format, import, kiến trúc layer) được ép bằng build — xem mục 20.

## Mục lục
1. [Quyết định chung](#1-quyết-định-chung)
2. [Package và phụ thuộc giữa các tầng](#2-package-và-phụ-thuộc-giữa-các-tầng)
3. [Đặt tên](#3-đặt-tên)
4. [Tiền và thời gian](#4-tiền-và-thời-gian)
5. [Dependency injection và Lombok](#5-dependency-injection-và-lombok)
6. [DTO, validation và mapping](#6-dto-validation-và-mapping)
7. [JPA entity](#7-jpa-entity)
8. [Transaction](#8-transaction)
9. [Exception và mã lỗi](#9-exception-và-mã-lỗi)
10. [REST API](#10-rest-api)
11. [Kafka](#11-kafka)
12. [Redis](#12-redis)
13. [Logging](#13-logging)
14. [Null và Optional](#14-null-và-optional)
15. [Viết code Java](#15-viết-code-java)
16. [Cấu hình](#16-cấu-hình)
17. [Database và migration](#17-database-và-migration)
18. [Test](#18-test)
19. [Git](#19-git)
20. [Công cụ kiểm tra tự động](#20-công-cụ-kiểm-tra-tự-động)
21. [Code mẫu chuẩn](#21-code-mẫu-chuẩn)

---

## 1. Quyết định chung

| Chủ đề | Quyết định |
|---|---|
| Java | 21. Dùng `record`, `switch` pattern matching, text block khi phù hợp |
| DTO | Java `record` |
| Mapping DTO ↔ entity | **MapStruct** |
| Lombok | **Hạn chế**, chỉ các annotation ở mục 5 |
| Validation | Jakarta Bean Validation (`@NotNull`, `@Positive`...) |
| HTTP client gọi service khác | Spring `RestClient` (đồng bộ) |
| JSON | Jackson, field `camelCase` |
| Ngôn ngữ trong code | Tên class/biến/method: **tiếng Anh**. Comment, message log: tiếng Anh hoặc tiếng Việt không dấu, nhất quán trong một service |

---

## 2. Package và phụ thuộc giữa các tầng

```
vn.bank.<service>/
├── api/                 # Controller, Request/Response DTO, mapper API
├── application/         # Use case, @Transactional, điều phối
├── domain/              # Entity, enum, value object, quy tắc nghiệp vụ, ErrorCode
├── infrastructure/
│   ├── persistence/     # Spring Data repository, query
│   ├── messaging/       # Kafka producer/consumer, outbox
│   └── client/          # RestClient gọi service khác
└── config/              # @Configuration, @ConfigurationProperties
```

**Quy tắc phụ thuộc** (ép bằng ArchUnit):

| Tầng | Được dùng | Không được dùng |
|---|---|---|
| `api` | `application`, `domain` | `infrastructure` |
| `application` | `domain`, `infrastructure` | `api` |
| `domain` | chỉ JDK, JPA annotation, `common-core` | `api`, `application`, `infrastructure`, Spring bean |
| `infrastructure` | `domain` | `api`, `application` |

- Controller **không** gọi thẳng repository.
- Consumer Kafka (trong `infrastructure/messaging`) chỉ nhận message rồi gọi sang `application` — xem mục 11. Ngoại lệ duy nhất cho chiều `infrastructure → application`: consumer và job scheduler được gọi application service.
- Logic nghiệp vụ nằm trong `domain` (method của entity hoặc domain service), **không** nằm trong controller hay consumer.

```java
// ❌ Logic nghiệp vụ trong application service, entity chỉ là túi dữ liệu
if (account.getBalance().compareTo(amount) < 0) throw ...;
account.setBalance(account.getBalance().subtract(amount));

// ✅ Entity tự bảo vệ quy tắc của nó
account.debit(amount);   // tự kiểm tra trạng thái và số dư, ném BusinessException nếu vi phạm
```

---

## 3. Đặt tên

### 3.1. Class

| Loại | Quy tắc | Ví dụ |
|---|---|---|
| Controller | `<Resource>Controller` | `TransferController` |
| Controller nội bộ | `<Resource>InternalController` | `PostingInternalController` |
| Use case | `<Động từ><Danh từ>Service` | `CreateTransferService`, `ConfirmOtpService` |
| Repository | `<Entity>Repository` | `AccountRepository` |
| Request / Response | `<Hành động><Resource>Request`, `<Resource>Response` | `CreateTransferRequest`, `TransferResponse` |
| Mapper | `<Resource>Mapper` | `TransferMapper` |
| Event | Danh từ + động từ quá khứ | `TransferCompleted`, `BalanceChanged` |
| Producer / Consumer | `<Event>Producer`, `<Event>Consumer` | `NapasResultConsumer` |
| REST client | `<Service>Client` | `AccountClient` |
| Cấu hình | `<Chủ đề>Properties`, `<Chủ đề>Config` | `TransferLimitProperties`, `KafkaConfig` |
| Mã lỗi | `<Service>ErrorCode` (enum) | `TransferErrorCode` |
| Job | `<Việc>Job` | `OutboxPublisherJob`, `NapasInquiryJob` |

### 3.2. Method

| Tiền tố | Ý nghĩa |
|---|---|
| `findXxx` | Có thể không có kết quả → trả `Optional` hoặc `List` |
| `getXxx` | Chắc chắn có, không có thì ném exception `*_NOT_FOUND` |
| `existsXxx` | Trả `boolean` |
| `createXxx`, `updateXxx`, `deleteXxx` | Thay đổi dữ liệu |
| `isXxx`, `canXxx`, `hasXxx` | Kiểm tra điều kiện, trả `boolean` |
| `toXxx` | Chuyển đổi kiểu (trong mapper, value object) |

### 3.3. Các tên khác

| Đối tượng | Quy tắc | Ví dụ |
|---|---|---|
| Hằng số | `UPPER_SNAKE_CASE` | `MAX_OTP_ATTEMPTS` |
| Mã lỗi | `UPPER_SNAKE_CASE`, bắt đầu bằng danh từ chủ thể | `ACCOUNT_NOT_ACTIVE`, `OTP_EXPIRED` |
| Bảng, cột Oracle | `UPPER_SNAKE_CASE`, tên bảng số ít | `TRANSFER`, `LEDGER_ENTRY`, `CREATED_AT` |
| Khóa chính | `<TÊN_BẢNG>_ID` (trừ khóa nghiệp vụ như `ACCOUNT_NO`) | `TRANSFER_ID` |
| Constraint, index | `PK_`, `FK_`, `UK_`, `CK_`, `IX_` + bảng + cột | `UK_TRANSFER_CIF_IDEM_KEY`, `IX_LEDGER_ENTRY_ACC_CREATED` |
| Sequence | `SEQ_<TÊN>` | `SEQ_CIF` |
| Kafka topic | `<domain>.<loại>` chữ thường | `transfer.events`, `napas.requests` |
| Redis key | `<domain>:<mục đích>:<id>` | `otp:fail:{transferId}` |
| URL | danh từ số nhiều, `kebab-case` | `/api/v1/transfers/{id}/otp/resend` |
| Thuộc tính cấu hình | `bank.<service>.<nhóm>.<tên>` | `bank.transfer.otp.ttl` |

---

## 4. Tiền và thời gian

### 4.1. Tiền
- Luôn dùng `BigDecimal`. **Không bao giờ** dùng `double`, `float` (sai số nhị phân làm lệch tiền).
- Tạo từ `String` hoặc `long`, không tạo từ `double`.
- So sánh bằng `compareTo`, **không** dùng `equals` (`equals` so cả scale: `1.0` ≠ `1.00`).
- Scale 2, làm tròn `RoundingMode.HALF_UP`. Cột Oracle `NUMBER(19,2)`.
- Số tiền phải `> 0` khi nhận vào. Hướng tiền (+/−) thể hiện bằng `DIRECTION` (DEBIT/CREDIT), không bằng số âm.

```java
// ❌
new BigDecimal(0.1);                 // = 0.1000000000000000055511...
if (amount.equals(BigDecimal.ZERO))  // false nếu amount = 0.00

// ✅
new BigDecimal("0.1");
BigDecimal.valueOf(100_000L);
if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(INVALID_AMOUNT, ...);
fee = amount.multiply(rate).setScale(0, RoundingMode.HALF_UP);
```

### 4.2. Thời gian
- Thời điểm xảy ra sự kiện: `Instant`, lưu DB dạng UTC (`hibernate.jdbc.time_zone=UTC`).
- Ngày nghiệp vụ (hạn mức ngày, đối soát, sao kê): `LocalDate` theo múi giờ `Asia/Ho_Chi_Minh`, lấy từ hằng số `BankZone.ZONE`.
- **Không** gọi `Instant.now()`, `LocalDate.now()` trực tiếp. Inject bean `Clock` để test được.

```java
// ❌
var today = LocalDate.now();

// ✅
private final Clock clock;
var today = LocalDate.now(clock.withZone(BankZone.ZONE));
```

---

## 5. Dependency injection và Lombok

- Chỉ dùng **constructor injection**, field `private final`. Không dùng `@Autowired` trên field hoặc setter.
- Lombok **chỉ được dùng**:

| Annotation | Dùng cho |
|---|---|
| `@RequiredArgsConstructor` | Bean Spring (service, controller, consumer...) |
| `@Slf4j` | Class cần log |
| `@Getter` | JPA entity |
| `@Builder` | Test data builder, value object phức tạp |

- **Cấm**: `@Data`, `@Setter` trên entity, `@SneakyThrows`, `@EqualsAndHashCode` trên entity, `@AllArgsConstructor` trên entity.
  Lý do: `@Data`/`@EqualsAndHashCode` trên entity JPA gây lỗi với lazy loading và proxy; `@Setter` phá vỡ việc entity tự bảo vệ quy tắc nghiệp vụ.
- DTO đã là `record` thì không cần Lombok.

---

## 6. DTO, validation và mapping

- **Không bao giờ** trả entity ra API hoặc đưa entity vào event Kafka. Luôn qua DTO/payload.
- Request validate định dạng bằng Jakarta annotation ở tầng `api`, controller có `@Valid`.
- Quy tắc nghiệp vụ (đủ số dư, hạn mức, trạng thái) kiểm tra trong `domain`/`application`, **không** dùng annotation.

```java
public record CreateTransferRequest(
        @NotBlank @Size(max = 20) String sourceAccount,
        @NotBlank @Size(max = 20) String destAccount,
        @Size(max = 10) String destBankCode,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
        @Size(max = 210) String description) {
}
```

- Mapper MapStruct đặt ở tầng sử dụng nó (`api` hoặc `infrastructure/messaging`), cấu hình chung:

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TransferMapper {
    TransferResponse toResponse(Transfer transfer);
}
```
`unmappedTargetPolicy = ERROR`: thêm field vào DTO mà quên map → lỗi lúc build, không lỗi âm thầm.

---

## 7. JPA entity

- Constructor không tham số để `protected` (JPA cần nhưng code nghiệp vụ không được dùng).
- Tạo entity qua static factory method có tên nghiệp vụ, kiểm tra quy tắc ngay khi tạo.
- Thay đổi trạng thái qua method nghiệp vụ, không có setter public.
- Enum lưu dạng chuỗi: `@Enumerated(EnumType.STRING)`.
- Có `@Version` cho optimistic lock.
- Quan hệ mặc định `FetchType.LAZY`. Không dùng quan hệ hai chiều nếu không thật sự cần.
- `equals`/`hashCode` (nếu cần) chỉ dựa trên id, viết tay.

```java
@Entity
@Table(name = "ACCOUNT")
@Getter
public class Account {

    @Id
    @Column(name = "ACCOUNT_NO")
    private String accountNo;

    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    private BigDecimal balance;

    @Version
    private long version;

    protected Account() {
    }

    public static Account open(String accountNo, String cif) { ... }

    public void debit(BigDecimal amount) {
        if (status != AccountStatus.ACTIVE) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_NOT_ACTIVE, "Account " + accountNo + " is " + status);
        }
        if (balance.compareTo(amount) < 0) {
            throw new BusinessException(AccountErrorCode.INSUFFICIENT_BALANCE, "Insufficient balance");
        }
        balance = balance.subtract(amount);
    }
}
```

---

## 8. Transaction

- `@Transactional` **chỉ** đặt trên method public của class trong `application`. Không đặt ở controller, repository, consumer.
- Method chỉ đọc: `@Transactional(readOnly = true)`.
- **Không** gọi REST sang service khác và **không** gửi Kafka trực tiếp bên trong transaction DB.
  Lý do: transaction giữ khóa DB trong lúc chờ mạng; và nếu rollback thì lời gọi/message đã gửi không thu hồi được. Event → ghi **outbox** trong transaction (BANK-046). Lời gọi REST → làm trước hoặc sau transaction.
- Không gọi method `@Transactional` từ chính class đó (self-invocation không đi qua proxy nên không có transaction).
- Việc chỉ được làm khi commit thành công (xóa cache, ...) → `TransactionSynchronization.afterCommit`.
- Transaction ghi tiền đặt `timeout` (mặc định 10 giây).

---

## 9. Exception và mã lỗi

- Lỗi nghiệp vụ: ném `BusinessException(ErrorCode, message)` từ `common-core`.
- Mỗi service có một enum `<Service>ErrorCode implements ErrorCode`, khai báo HTTP status cho từng mã.
- `GlobalExceptionHandler` chuyển exception thành response lỗi chuẩn. Controller **không** tự `try/catch` để tạo response lỗi.
- **Không** catch rồi bỏ qua. **Không** catch `Exception`/`Throwable` chung chung, trừ ở ranh giới (handler, consumer, job) để log và chuyển hướng.
- Khi bọc exception, luôn truyền `cause`.
- **Không** log rồi ném lại cùng một lỗi (gây log trùng). Hoặc log, hoặc ném — log ở nơi xử lý cuối.

```java
public enum TransferErrorCode implements ErrorCode {
    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY),
    DAILY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY),
    NAPAS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);
    ...
}
```

```java
// ❌
try { accountClient.post(...); } catch (Exception e) { log.error("error"); }

// ❌
catch (IOException e) { log.error("Failed", e); throw new IllegalStateException("Failed", e); }

// ✅
catch (ResourceAccessException e) {
    throw new TechnicalException(TransferErrorCode.ACCOUNT_SERVICE_TIMEOUT, "Posting timeout for " + transferId, e);
}
```

---

## 10. REST API

| Quy tắc | Chi tiết |
|---|---|
| Prefix | Public: `/api/v1/...`. Nội bộ giữa service: `/internal/...` (không route qua gateway) |
| URL | Danh từ số nhiều; hành động không phải CRUD dùng sub-resource: `POST /transfers/{id}/confirm` |
| JSON | `camelCase`; thời gian ISO-8601 UTC (`2026-11-21T03:00:00Z`); tiền là số dạng thường, không dạng khoa học (`1000000.00`) |
| Tạo giao dịch | Bắt buộc header `Idempotency-Key` |
| Phân trang | `page` (từ 0), `size` (mặc định 20, tối đa 100). Response có `items`, `page`, `size`, `totalElements` hoặc `nextCursor` |
| Lỗi | Format chung `ErrorResponse` (code, message, traceId, timestamp, details) |

**HTTP status**:

| Status | Khi nào |
|---|---|
| 200 | Đọc, cập nhật thành công |
| 201 | Tạo mới thành công (kèm id) |
| 400 | Sai định dạng, thiếu trường |
| 401 / 403 | Chưa xác thực / không có quyền |
| 404 | Không tìm thấy |
| 409 | Xung đột: trùng dữ liệu, sửa đồng thời |
| 422 | Đúng định dạng nhưng vi phạm quy tắc nghiệp vụ |
| 429 | Vượt rate limit |
| 503 | Phụ thuộc không sẵn sàng, client được retry |

---

## 11. Kafka

- Payload event là `record` trong `common-events`, bọc trong `EventEnvelope`.
- Event đặt tên ở thì quá khứ, mô tả việc **đã xảy ra** (`TransferCompleted`), không phải mệnh lệnh.
  Ngoại lệ: message yêu cầu đối tác (`InterbankTransferRequested`).
- Thay đổi payload theo hướng tương thích ngược: chỉ thêm field, không đổi tên/xóa field.
- Message key = id của aggregate.
- Gửi event nghiệp vụ qua outbox (mục 8).
- Consumer chỉ làm 3 việc: nhận → kiểm tra idempotent → gọi application service. Không viết logic nghiệp vụ trong method `@KafkaListener`.
- Consumer phải idempotent (bảng `PROCESSED_MESSAGE`).

```java
@Component
@RequiredArgsConstructor
class NapasResultConsumer {

    private final HandleNapasResultService handleNapasResultService;

    @KafkaListener(topics = Topics.NAPAS_RESPONSES, groupId = "transfer-service")
    void onMessage(EventEnvelope<InterbankTransferResult> event) {
        handleNapasResultService.handle(event.eventId(), event.payload());
    }
}
```

---

## 12. Redis

- Redis **không** phải nguồn dữ liệu gốc. Mất dữ liệu Redis thì hệ thống vẫn đúng (có thể chậm hơn).
- Mọi key **phải có TTL**.
- Tên key theo mục 3.3 và được khai báo trong một class hằng số `RedisKeys` của service.
- Thao tác kiểm tra rồi ghi (check-then-act) phải atomic: dùng `SET NX`, `INCRBY` hoặc Lua script. Không `GET` rồi `SET` riêng lẻ.
- Redis lỗi ở luồng đọc cache → fallback DB, log WARN. Redis lỗi ở luồng bắt buộc (OTP, idempotency) → trả 503.

---

## 13. Logging

- Dùng SLF4J (`@Slf4j`), log có tham số: `log.info("Transfer {} completed", transferId)`. Không nối chuỗi.
- Không dùng `System.out`, `printStackTrace()`.

| Level | Khi nào |
|---|---|
| `ERROR` | Cần người can thiệp: dữ liệu lệch, lệnh UNKNOWN, message vào DLQ, lỗi không mong đợi |
| `WARN` | Bất thường nhưng hệ thống tự xử lý được: retry, fallback, phản hồi trùng |
| `INFO` | Mốc nghiệp vụ: tạo lệnh, đổi trạng thái, job chạy xong |
| `DEBUG` | Chi tiết kỹ thuật phục vụ debug, tắt ở môi trường thường |

- **Không** log: OTP, token, mật khẩu, CCCD đầy đủ, toàn bộ request/response body.
- Số tài khoản trong log được che: `1000****5678`.
- Log lỗi kèm exception làm tham số cuối: `log.error("Posting failed for {}", transferId, e)`.

---

## 14. Null và Optional

- Method tìm kiếm có thể không có kết quả → trả `Optional<T>`.
- Collection trả về rỗng, **không** trả `null`.
- **Không** dùng `Optional` làm tham số method hoặc field.
- Không gọi `Optional.get()` khi chưa kiểm tra. Dùng `orElseThrow(...)`.

```java
// ✅
public Account getAccount(String accountNo) {
    return accountRepository.findById(accountNo)
            .orElseThrow(() -> new BusinessException(ACCOUNT_NOT_FOUND, "Account not found: " + accountNo));
}
```

---

## 15. Viết code Java

- Không wildcard import (`import java.util.*`).
- `var` chỉ dùng khi kiểu đã rõ từ vế phải (`var account = Account.open(...)`).
- Không magic number/string: đưa vào hằng số hoặc cấu hình.
- Method nên ≤ 30 dòng, class nên ≤ 300 dòng. Vượt thì tách.
- Không để code bị comment, `TODO` không kèm mã task (`// TODO(BANK-044): ...`).
- Comment giải thích **vì sao**, không lặp lại code làm gì.
- Ưu tiên đọc dễ hơn ngắn gọn: stream lồng nhiều tầng → đổi sang vòng lặp.
- Class chỉ dùng trong package → để package-private (không `public`).

---

## 16. Cấu hình

- Gom cấu hình vào `@ConfigurationProperties` dạng `record` có validate. Không rải `@Value` khắp nơi.
- Prefix `bank.<service>`.
- **Không** commit secret (mật khẩu DB, client secret). Đọc từ biến môi trường, có giá trị mẫu trong `.env.example`.
- Profile: `local` (chạy trên máy), `docker` (chạy trong compose), `test`.

```java
@Validated
@ConfigurationProperties(prefix = "bank.transfer.otp")
public record OtpProperties(
        @NotNull Duration ttl,
        @Min(1) int maxAttempts,
        @Min(1) int maxResend) {
}
```

---

## 17. Database và migration

- Mỗi thay đổi DB là một file Flyway `V<số>__<mo_ta_snake_case>.sql`, ví dụ `V3__create_transfer_table.sql`.
- **Không** sửa file migration đã có trên `main`. Muốn đổi → tạo migration mới.
- **Không** viết `DROP`, `TRUNCATE`, xóa cột khi chưa được người duyệt đồng ý.
- Tạo bảng phải kèm constraint (PK, UK, CK, FK trong cùng schema) và index cần cho truy vấn của task.
- Thêm `COMMENT ON TABLE` / `COMMENT ON COLUMN` cho bảng nghiệp vụ.
- Query phức tạp dùng `@Query` native hoặc JPQL đặt tên rõ; không tạo query bằng cách nối chuỗi (nguy cơ SQL injection).
- Tránh N+1: dùng `JOIN FETCH` hoặc truy vấn projection khi cần dữ liệu liên quan.

---

## 18. Test

| Loại | Hậu tố | Chạy bởi | Dùng cho |
|---|---|---|---|
| Unit | `*Test` | surefire | `domain`, logic trong `application` (mock phụ thuộc) |
| Integration | `*IT` | failsafe | Repository, API, Kafka, Redis, luồng nhiều tầng — Testcontainers |

- Tên method: `should_<kết quả>_when_<điều kiện>`, ví dụ `should_rejectTransfer_when_dailyLimitExceeded`.
- Cấu trúc **given / when / then**, có comment ngăn cách.
- Assertion dùng **AssertJ**.
- Mỗi acceptance criteria của task có ít nhất một test, tên test phản ánh AC đó.
- Không dùng `Thread.sleep` để chờ bất đồng bộ → dùng **Awaitility**.
- Thời gian trong test dùng `Clock.fixed(...)`.
- Không dùng H2 thay Oracle.
- Test độc lập, không phụ thuộc thứ tự chạy; tự tạo dữ liệu của mình.
- **Không** `@Disabled` hoặc xóa test để build pass.

```java
@Test
void should_rejectTransfer_when_dailyLimitExceeded() {
    // given
    givenTransferredToday(SOURCE_ACCOUNT, new BigDecimal("450000000"));

    // when
    var thrown = catchThrowable(() -> createTransferService.create(requestOf("60000000")));

    // then
    assertThat(thrown)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(TransferErrorCode.DAILY_LIMIT_EXCEEDED);
}
```

---

## 19. Git

- Mỗi task một branch: `feature/BANK-xxx-<slug>`; sửa lỗi: `fix/BANK-xxx-<slug>`.
- Commit theo Conventional Commits, có mã task:
  - `feat(BANK-041): them API khoi tao chuyen khoan`
  - `fix(BANK-043): hoan han muc khi lenh that bai`
  - `test(BANK-032): them test chuyen khoan dong thoi`
  - `refactor`, `docs`, `chore` tương tự
- Một commit làm một việc, build phải pass ở mọi commit.

---

## 20. Công cụ kiểm tra tự động

Những quy tắc dưới đây được ép bằng build. Vi phạm → `mvnw verify` fail → agent phải sửa.

| Công cụ | Kiểm tra |
|---|---|
| **Spotless** (palantir-java-format) | Format code, thứ tự import, không wildcard import. Sửa tự động: `./mvnw spotless:apply` |
| **Checkstyle** | Cấm field injection, `System.out`, `printStackTrace`; độ dài method/class |
| **ArchUnit** | Quy tắc tầng mục 2; controller không dùng repository; không dùng `double`/`float` cho field tiền; `@Transactional` chỉ ở `application`; cấm Lombok `@Data`/`@Setter` trên `@Entity` |
| **maven-enforcer** | Java 21, Maven tối thiểu, không trùng dependency |

Ví dụ một rule ArchUnit:
```java
@ArchTest
static final ArchRule controllers_should_not_use_repositories =
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
```

---

## 21. Code mẫu chuẩn

> Agent bắt chước code có sẵn chính xác hơn làm theo mô tả. Khi viết code mới, **tìm và làm theo** code mẫu dưới đây.

| Loại code | Code mẫu |
|---|---|
| API + use case + entity + test | _(cập nhật sau khi BANK-013 được duyệt: `customer-service`)_ |
| Kafka consumer idempotent | _(cập nhật sau BANK-061)_ |
| Outbox | _(cập nhật sau BANK-046)_ |
| Integration test Testcontainers | _(cập nhật sau BANK-006)_ |
