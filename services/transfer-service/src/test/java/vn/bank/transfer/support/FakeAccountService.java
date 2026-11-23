package vn.bank.transfer.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * account-service gia (JDK HttpServer) cho {@code GET /internal/accounts/{accountNo}}: tai khoan da khai bao tra 200
 * kem CIF va trang thai, con lai tra 404 {@code ACCOUNT_NOT_FOUND} giong account-service that.
 */
public final class FakeAccountService implements AutoCloseable {

    private static final String ACCOUNTS_PATH = "/internal/accounts/";
    private static final int OK = 200;
    private static final int NOT_FOUND = 404;

    private final HttpServer server;
    private final ExecutorService executor;
    private final Map<String, String> bodyByAccountNo = new ConcurrentHashMap<>();

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
            server.createContext(ACCOUNTS_PATH, fake::handle);
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

    /** {@code cif} null: tai khoan GL cua ngan hang. */
    public void givenAccount(String accountNo, String cif, String status) {
        var cifJson = cif == null ? "null" : "\"" + cif + "\"";
        var accountType = cif == null ? "GL" : "CURRENT";
        bodyByAccountNo.put(accountNo, """
                {"accountNo":"%s","cif":%s,"accountType":"%s","currency":"VND","balance":1000000.00,
                 "availableBalance":1000000.00,"status":"%s","createdAt":"2026-11-23T03:00:00Z"}""".formatted(accountNo, cifJson, accountType, status));
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdown();
    }

    private void handle(HttpExchange exchange) throws IOException {
        var accountNo = exchange.getRequestURI().getPath().substring(ACCOUNTS_PATH.length());
        var body = bodyByAccountNo.get(accountNo);
        if (body == null) {
            respond(exchange, NOT_FOUND, """
                    {"code":"ACCOUNT_NOT_FOUND","message":"Account not found","details":[]}""");
            return;
        }
        respond(exchange, OK, body);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
