package vn.bank.transfer.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * account-service gia (JDK HttpServer). {@code GET /internal/accounts/{accountNo}}: tai khoan da khai bao tra 200 kem
 * CIF, trang thai va so du, con lai 404 {@code ACCOUNT_NOT_FOUND}. {@code POST /internal/postings}: mo phong posting
 * that, idempotent theo {@code (referenceType, referenceId)} (da co journal → 200, khong ghi lai), tai khoan khong
 * ACTIVE → 422 {@code ACCOUNT_NOT_ACTIVE}, so du khong du → 422 {@code INSUFFICIENT_BALANCE}; ghi duoc thi tru/cong so
 * du. Test gia lap duoc loi ky thuat: response cham sau khi da ghi (client timeout), hoac luon tra 503.
 */
public final class FakeAccountService implements AutoCloseable {

    private static final String ACCOUNTS_PATH = "/internal/accounts/";
    private static final String POSTINGS_PATH = "/internal/postings";
    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int NOT_FOUND = 404;
    private static final int UNPROCESSABLE = 422;
    private static final int SERVICE_UNAVAILABLE = 503;
    private static final String DEFAULT_BALANCE = "1000000000.00";
    private static final String ACTIVE = "ACTIVE";

    private final HttpServer server;
    private final ExecutorService executor;
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();
    // Khoa chung cho so du va journal: posting dong thoi xep hang nhu khoa FOR UPDATE cua account-service that
    private final Object ledgerLock = new Object();
    private final Map<String, FakeAccount> accounts = new HashMap<>();
    private final Map<String, List<String>> journalLinesByReference = new HashMap<>();
    private final Map<String, AtomicInteger> postingRequestsByReferenceId = new ConcurrentHashMap<>();
    private final Map<String, Duration> responseDelayByReferenceId = new ConcurrentHashMap<>();
    private final Set<String> unavailableReferenceIds = ConcurrentHashMap.newKeySet();
    private final Map<String, String> rejectionCodeByReferenceId = new ConcurrentHashMap<>();

    private FakeAccountService(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    public static FakeAccountService start() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            // Nhieu luong de test gui dong thoi khong bi xep hang o day
            var executor = Executors.newCachedThreadPool();
            var fake = new FakeAccountService(server, executor);
            server.createContext(ACCOUNTS_PATH, fake::handleAccount);
            server.createContext(POSTINGS_PATH, fake::handlePosting);
            server.setExecutor(executor);
            server.start();
            return fake;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start fake account-service", e);
        }
    }

    public String uri() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /**
     * {@code cif} null: tai khoan GL cua ngan hang. So du mac dinh du lon cho moi lenh trong han muc ngay (500 trieu)
     * cong phi.
     */
    public void givenAccount(String accountNo, String cif, String status) {
        givenAccount(accountNo, cif, status, DEFAULT_BALANCE);
    }

    /** Khai bao (hoac ghi de) tai khoan; so du thuc te = so du kha dung = {@code balance}. */
    public void givenAccount(String accountNo, String cif, String status, String balance) {
        synchronized (ledgerLock) {
            accounts.put(accountNo, new FakeAccount(cif, status, new BigDecimal(balance)));
        }
    }

    /** Lan posting dau tien cua {@code referenceId} van ghi so du nhung tra response sau {@code delay}. */
    public void givenPostingResponseDelayedOnce(String referenceId, Duration delay) {
        responseDelayByReferenceId.put(referenceId, delay);
    }

    /** Moi lan posting cua {@code referenceId} bi tu choi 422 voi ma {@code errorCode}, khong ghi gi. */
    public void givenPostingRejected(String referenceId, String errorCode) {
        rejectionCodeByReferenceId.put(referenceId, errorCode);
    }

    /** Moi lan posting cua {@code referenceId} tra 503 va khong ghi gi. */
    public void givenPostingUnavailable(String referenceId) {
        unavailableReferenceIds.add(referenceId);
    }

    public BigDecimal balanceOf(String accountNo) {
        synchronized (ledgerLock) {
            return accounts.get(accountNo).balance();
        }
    }

    /** So request {@code POST /internal/postings} da nhan cho {@code referenceId} (ke ca lan loi). */
    public int postingRequestCount(String referenceId) {
        var count = postingRequestsByReferenceId.get(referenceId);
        return count == null ? 0 : count.get();
    }

    /** Cac dong cua journal TRANSFER da ghi cho {@code referenceId}, dang "accountNo DIRECTION amount"; rong neu chua. */
    public List<String> transferJournalLines(String referenceId) {
        synchronized (ledgerLock) {
            return journalLinesByReference.getOrDefault(referenceKey("TRANSFER", referenceId), List.of());
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdown();
    }

    private void handleAccount(HttpExchange exchange) throws IOException {
        var accountNo = exchange.getRequestURI().getPath().substring(ACCOUNTS_PATH.length());
        String body;
        synchronized (ledgerLock) {
            var account = accounts.get(accountNo);
            body = account == null ? null : account.toJson(accountNo);
        }
        if (body == null) {
            respondError(exchange, NOT_FOUND, "ACCOUNT_NOT_FOUND");
            return;
        }
        respond(exchange, OK, body);
    }

    private void handlePosting(HttpExchange exchange) throws IOException {
        var request = jsonMapper.readTree(exchange.getRequestBody());
        var referenceId = request.get("referenceId").asString();
        postingRequestsByReferenceId
                .computeIfAbsent(referenceId, key -> new AtomicInteger())
                .incrementAndGet();
        if (unavailableReferenceIds.contains(referenceId)) {
            respondError(exchange, SERVICE_UNAVAILABLE, "ACCOUNT_BUSY");
            return;
        }
        var rejectionCode = rejectionCodeByReferenceId.get(referenceId);
        if (rejectionCode != null) {
            respondError(exchange, UNPROCESSABLE, rejectionCode);
            return;
        }
        var result = post(request.get("referenceType").asString(), referenceId, request.get("lines"));
        var delay = responseDelayByReferenceId.remove(referenceId);
        if (delay != null) {
            sleep(delay);
        }
        if (result.errorCode() != null) {
            respondError(exchange, result.status(), result.errorCode());
            return;
        }
        respond(exchange, result.status(), """
                {"referenceId":"%s","entries":[]}""".formatted(referenceId));
    }

    private PostingResult post(String referenceType, String referenceId, JsonNode lines) {
        synchronized (ledgerLock) {
            var key = referenceKey(referenceType, referenceId);
            if (journalLinesByReference.containsKey(key)) {
                return new PostingResult(OK, null);
            }
            var rejection = rejectionOf(lines);
            if (rejection != null) {
                return rejection;
            }
            var recorded = new ArrayList<String>();
            for (var line : lines) {
                var accountNo = line.get("accountNo").asString();
                var amount = amountOf(line);
                var debit = "DEBIT".equals(line.get("direction").asString());
                accounts.put(accountNo, accounts.get(accountNo).apply(debit ? amount.negate() : amount));
                recorded.add(accountNo + " " + line.get("direction").asString() + " " + amount.toPlainString());
            }
            journalLinesByReference.put(key, List.copyOf(recorded));
            return new PostingResult(CREATED, null);
        }
    }

    private PostingResult rejectionOf(JsonNode lines) {
        for (var line : lines) {
            var account = accounts.get(line.get("accountNo").asString());
            if (account == null) {
                return new PostingResult(NOT_FOUND, "ACCOUNT_NOT_FOUND");
            }
            if (!ACTIVE.equals(account.status())) {
                return new PostingResult(UNPROCESSABLE, "ACCOUNT_NOT_ACTIVE");
            }
            var amount = amountOf(line);
            var debit = "DEBIT".equals(line.get("direction").asString());
            if (debit && account.cif() != null && account.balance().compareTo(amount) < 0) {
                return new PostingResult(UNPROCESSABLE, "INSUFFICIENT_BALANCE");
            }
        }
        return null;
    }

    private static BigDecimal amountOf(JsonNode line) {
        return line.get("amount").decimalValue().setScale(2, RoundingMode.HALF_UP);
    }

    private static String referenceKey(String referenceType, String referenceId) {
        return referenceType + ":" + referenceId;
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void respondError(HttpExchange exchange, int status, String code) throws IOException {
        respond(exchange, status, """
                {"code":"%s","message":"%s","details":[]}""".formatted(code, code));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private record PostingResult(int status, String errorCode) {}

    private record FakeAccount(String cif, String status, BigDecimal balance) {

        FakeAccount apply(BigDecimal delta) {
            return new FakeAccount(cif, status, balance.add(delta));
        }

        String toJson(String accountNo) {
            var cifJson = cif == null ? "null" : "\"" + cif + "\"";
            var accountType = cif == null ? "GL" : "CURRENT";
            return """
                    {"accountNo":"%s","cif":%s,"accountType":"%s","currency":"VND","balance":%s,
                     "availableBalance":%s,"status":"%s","createdAt":"2026-11-23T03:00:00Z"}""".formatted(
                            accountNo, cifJson, accountType, balance.toPlainString(), balance.toPlainString(), status);
        }
    }
}
