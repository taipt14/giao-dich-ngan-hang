package vn.bank.gateway.support;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service phia sau gia (JDK HttpServer): tra ve ten service va ghi lai header cua request cuoi cung nhan duoc, de test
 * kiem request duoc route dung service va mang dung header.
 */
public final class FakeBackendService implements AutoCloseable {

    private final String name;
    private final HttpServer server;
    private final AtomicReference<Headers> lastRequestHeaders = new AtomicReference<>();

    private FakeBackendService(String name, HttpServer server) {
        this.name = name;
        this.server = server;
    }

    public static FakeBackendService start(String name) {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            var backend = new FakeBackendService(name, server);
            var body = ("{\"service\":\"" + name + "\"}").getBytes(StandardCharsets.UTF_8);
            server.createContext("/", exchange -> {
                backend.lastRequestHeaders.set(exchange.getRequestHeaders());
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return backend;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start fake backend " + name, e);
        }
    }

    /** URI cua mot port vua duoc giai phong: khong co service nao lang nghe, ket noi toi se bi tu choi. */
    public static String unreachableUri() {
        try (var socket = new ServerSocket(0)) {
            return "http://localhost:" + socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot find a free port", e);
        }
    }

    public String name() {
        return name;
    }

    public String uri() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** Header cua request cuoi cung; rong neu service chua nhan request nao. */
    public Optional<Headers> lastRequestHeaders() {
        return Optional.ofNullable(lastRequestHeaders.get());
    }

    public void reset() {
        lastRequestHeaders.set(null);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
