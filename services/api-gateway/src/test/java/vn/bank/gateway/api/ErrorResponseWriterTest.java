package vn.bank.gateway.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import tools.jackson.databind.json.JsonMapper;

class ErrorResponseWriterTest {

    private static final Instant NOW = Instant.parse("2026-11-21T03:00:00Z");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ErrorResponseWriter writer = new ErrorResponseWriter(jsonMapper, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void should_writeErrorResponseWithStatusNameAsCode_when_accessDenied() {
        // given
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/accounts"));

        // when
        writer.write(exchange, HttpStatus.FORBIDDEN, "Access denied").block();

        // then
        var response = exchange.getResponse();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        var body = jsonMapper.readTree(response.getBodyAsString().block());
        assertThat(body.path("code").asString()).isEqualTo("FORBIDDEN");
        assertThat(body.path("message").asString()).isEqualTo("Access denied");
        assertThat(body.path("timestamp").asString()).isEqualTo("2026-11-21T03:00:00Z");
        assertThat(body.path("details").isArray()).isTrue();
        assertThat(body.path("details").isEmpty()).isTrue();
    }
}
