package vn.bank.gateway.api;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;
import vn.bank.common.error.ErrorResponse;

/**
 * Ghi loi do gateway tu tra (401, 403, 404, 503...) theo {@link ErrorResponse} cua common-core, de client chi xu ly
 * mot format loi du loi den tu gateway hay tu service.
 */
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

    private final JsonMapper jsonMapper;
    private final Clock clock;

    /** Ma loi la ten {@link HttpStatus}, nhat quan voi loi framework cua GlobalExceptionHandler (BANK-004). */
    public Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status, String message) {
        return write(exchange, status, toCode(status), message);
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status, String code, String message) {
        // Chua co tracing (BANK-070) nen traceId de null, giong service servlet khi MDC trong
        var body = new ErrorResponse(code, message, null, clock.instant(), List.of());
        var bytes = jsonMapper.writeValueAsBytes(body);
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().setContentLength(bytes.length);
        return response.writeWith(
                Mono.fromSupplier(() -> response.bufferFactory().wrap(bytes)));
    }

    static String toCode(HttpStatusCode status) {
        var known = HttpStatus.resolve(status.value());
        return known != null ? known.name() : "HTTP_" + status.value();
    }
}
