package vn.bank.account.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * customer-service gia (JDK HttpServer) cho {@code GET /internal/customers/{cif}}: CIF da khai bao tra 200 kem trang
 * thai, CIF khai bao loi tra 500, con lai tra 404 {@code CUSTOMER_NOT_FOUND} giong customer-service that.
 */
public final class FakeCustomerService implements AutoCloseable {

    private static final String CUSTOMERS_PATH = "/internal/customers/";
    private static final int OK = 200;
    private static final int NOT_FOUND = 404;
    private static final int INTERNAL_ERROR = 500;

    private final HttpServer server;
    private final Map<String, String> statusByCif = new ConcurrentHashMap<>();
    private final Map<String, Boolean> failingCifs = new ConcurrentHashMap<>();

    private FakeCustomerService(HttpServer server) {
        this.server = server;
    }

    public static FakeCustomerService start() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            var fake = new FakeCustomerService(server);
            server.createContext(CUSTOMERS_PATH, fake::handle);
            server.start();
            return fake;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start fake customer-service", e);
        }
    }

    public String uri() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void givenCustomer(String cif, String status) {
        statusByCif.put(cif, status);
    }

    public void givenFailure(String cif) {
        failingCifs.put(cif, Boolean.TRUE);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        var cif = exchange.getRequestURI().getPath().substring(CUSTOMERS_PATH.length());
        if (failingCifs.containsKey(cif)) {
            respond(exchange, INTERNAL_ERROR, """
                    {"code":"INTERNAL_ERROR","message":"Internal server error","details":[]}""");
            return;
        }
        var status = statusByCif.get(cif);
        if (status == null) {
            respond(exchange, NOT_FOUND, """
                    {"code":"CUSTOMER_NOT_FOUND","message":"Customer not found: %s","details":[]}""".formatted(cif));
            return;
        }
        respond(exchange, OK, """
                {"cif":"%s","status":"%s"}""".formatted(cif, status));
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
