package vn.bank.gateway.api;

import java.util.List;
import java.util.function.Consumer;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Chuyen thong tin nguoi dung tu JWT da xac thuc xuong service phia sau qua header. Header cung ten do client gui len
 * luon bi xoa truoc, de client khong gia mao duoc danh tinh.
 */
@Component
class UserHeadersFilter implements GlobalFilter, Ordered {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String CIF_HEADER = "X-Cif";
    static final String ROLES_HEADER = "X-Roles";

    static final String CIF_CLAIM = "cif";
    static final String REALM_ACCESS_CLAIM = "realm_access";
    static final String ROLES_KEY = "roles";
    static final String ROLES_DELIMITER = ",";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(principal -> rewriteUserHeaders(
                        exchange, headers -> addUserHeaders(headers, ((JwtAuthenticationToken) principal).getToken())))
                // Request khong co token (health check) cung khong duoc mang header danh tinh tu client
                .switchIfEmpty(Mono.fromSupplier(() -> rewriteUserHeaders(exchange, headers -> {})))
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static ServerWebExchange rewriteUserHeaders(ServerWebExchange exchange, Consumer<HttpHeaders> userHeaders) {
        return exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(CIF_HEADER);
                    headers.remove(ROLES_HEADER);
                    userHeaders.accept(headers);
                }))
                .build();
    }

    private static void addUserHeaders(HttpHeaders headers, Jwt jwt) {
        headers.set(USER_ID_HEADER, jwt.getSubject());
        // User noi bo (vi du admin01) khong co CIF → khong gui header
        var cif = jwt.getClaimAsString(CIF_CLAIM);
        if (cif != null) {
            headers.set(CIF_HEADER, cif);
        }
        var roles = realmRoles(jwt);
        if (!roles.isEmpty()) {
            headers.set(ROLES_HEADER, String.join(ROLES_DELIMITER, roles));
        }
    }

    private static List<String> realmRoles(Jwt jwt) {
        var realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null || !(realmAccess.get(ROLES_KEY) instanceof List<?> roles)) {
            return List.of();
        }
        return roles.stream().map(String::valueOf).toList();
    }
}
