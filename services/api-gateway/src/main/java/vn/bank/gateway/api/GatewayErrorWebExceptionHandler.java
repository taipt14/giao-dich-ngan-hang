package vn.bank.gateway.api;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import vn.bank.common.error.CommonErrorCode;
import vn.bank.gateway.domain.GatewayErrorCode;

/**
 * Ranh gioi xu ly loi cua gateway (thay {@code GlobalExceptionHandler} chi danh cho servlet). Chay truoc handler mac
 * dinh cua Spring Boot (order -1) de moi loi deu tra {@code ErrorResponse}.
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    static final String SERVICE_UNAVAILABLE_MESSAGE = "Downstream service is unavailable";
    static final String INTERNAL_ERROR_MESSAGE = "Internal server error";
    static final String RATE_LIMIT_EXCEEDED_MESSAGE = "Too many requests, please retry later";

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        var path = exchange.getRequest().getPath().value();
        if (isDownstreamUnavailable(ex)) {
            log.warn("Downstream service unavailable for {}: {}", path, ex.getMessage());
            return errorResponseWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE_MESSAGE);
        }
        if (ex instanceof HttpClientErrorException.TooManyRequests) {
            // RequestRateLimiter bat throw-on-limit nem loi nay khi user vuot han muc request
            log.warn("Rate limit exceeded for {}", path);
            var code = GatewayErrorCode.RATE_LIMIT_EXCEEDED;
            return errorResponseWriter.write(
                    exchange, HttpStatus.valueOf(code.httpStatus()), code.code(), RATE_LIMIT_EXCEEDED_MESSAGE);
        }
        if (ex instanceof ResponseStatusException statusException) {
            return handleStatus(exchange, path, statusException);
        }
        log.error("Unexpected error for {}", path, ex);
        var code = CommonErrorCode.INTERNAL_ERROR;
        return errorResponseWriter.write(
                exchange, HttpStatus.valueOf(code.httpStatus()), code.code(), INTERNAL_ERROR_MESSAGE);
    }

    private Mono<Void> handleStatus(ServerWebExchange exchange, String path, ResponseStatusException ex) {
        var status = ex.getStatusCode();
        if (status.is5xxServerError()) {
            log.error("Request {} failed with status {}", path, status.value(), ex);
        } else {
            log.warn("Request {} rejected with status {}: {}", path, status.value(), ex.getReason());
        }
        // Reason cua framework (vi du "No static resource ...") la chi tiet ben trong, khong tra cho client
        var known = HttpStatus.resolve(status.value());
        var message = known != null ? known.getReasonPhrase() : ErrorResponseWriter.toCode(status);
        return errorResponseWriter.write(exchange, status, message);
    }

    /**
     * Khong ket noi duoc service phia sau, hoac service khong tra loi trong {@code response-timeout} (gateway bao 504).
     * Ca hai deu la phu thuoc khong san sang → 503, client duoc retry.
     */
    private static boolean isDownstreamUnavailable(Throwable ex) {
        if (ex instanceof ResponseStatusException statusException) {
            return statusException.getStatusCode().value() == HttpStatus.GATEWAY_TIMEOUT.value();
        }
        for (var current = ex; current != null; current = current.getCause()) {
            if (current instanceof ConnectException || current instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
