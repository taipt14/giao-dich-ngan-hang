package vn.bank.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import vn.bank.gateway.api.ErrorResponseWriter;

/**
 * Gateway la OAuth2 Resource Server: moi request phai co JWT hop le do Keycloak cap (issuer, chu ky, han, audience
 * {@code bank-gateway} cau hinh trong application.yml), tru health check.
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    static final String AUTHENTICATION_REQUIRED_MESSAGE = "A valid access token is required";
    static final String ACCESS_DENIED_MESSAGE = "Access denied";

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ErrorResponseWriter errorResponseWriter) {
        var entryPoint = authenticationEntryPoint(errorResponseWriter);
        var accessDeniedHandler = accessDeniedHandler(errorResponseWriter);
        return http
                // API stateless dung bearer token: khong session, khong CSRF, khong form/basic login
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .anyExchange()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    private static ServerAuthenticationEntryPoint authenticationEntryPoint(ErrorResponseWriter errorResponseWriter) {
        return (exchange, ex) -> {
            // Chi log ly do (thieu token, het han...), khong log token
            log.warn("Unauthenticated request {}: {}", exchange.getRequest().getPath(), ex.getMessage());
            return errorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED, AUTHENTICATION_REQUIRED_MESSAGE);
        };
    }

    private static ServerAccessDeniedHandler accessDeniedHandler(ErrorResponseWriter errorResponseWriter) {
        return (exchange, ex) -> {
            log.warn("Access denied for {}: {}", exchange.getRequest().getPath(), ex.getMessage());
            return errorResponseWriter.write(exchange, HttpStatus.FORBIDDEN, ACCESS_DENIED_MESSAGE);
        };
    }
}
