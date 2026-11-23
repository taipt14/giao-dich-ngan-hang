package vn.bank.gateway.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Goi gateway bang JDK HttpClient: doc duoc status/body cua moi response, ke ca 4xx/5xx. */
public final class GatewayTestClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final String baseUrl;

    public GatewayTestClient(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public HttpResponse<String> get(String path) {
        return get(path, Map.of());
    }

    public HttpResponse<String> getWithToken(String path, String token) {
        return get(path, Map.of("Authorization", "Bearer " + token));
    }

    public HttpResponse<String> get(String path, Map<String, String> headers) {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
        headers.forEach(request::header);
        try {
            return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("Call to gateway failed: " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling gateway: " + path, e);
        }
    }

    public static JsonNode json(HttpResponse<String> response) {
        return JSON.readTree(response.body());
    }
}
