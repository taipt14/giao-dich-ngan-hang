package vn.bank.gateway.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Thay Keycloak trong test: tu sinh khoa RSA, phuc vu JWKS qua HTTP va ky JWT co claim giong token cua realm
 * {@code bank} (BANK-010). Test khong phu thuoc Keycloak that.
 */
public final class TestJwtIssuer implements AutoCloseable {

    public static final String ISSUER = "http://localhost:8180/realms/bank";
    public static final String GATEWAY_AUDIENCE = "bank-gateway";
    public static final String CUSTOMER_SUBJECT = "4f1c2a3b-0000-4000-8000-000000000001";
    public static final String CUSTOMER_CIF = "C000000001";
    public static final String CUSTOMER_ROLE = "CUSTOMER";

    private static final String JWKS_PATH = "/realms/bank/protocol/openid-connect/certs";
    private static final String KEY_ID = "test-key";
    private static final int RSA_KEY_SIZE = 2048;
    private static final Duration TOKEN_LIFESPAN = Duration.ofMinutes(5);

    // JWT duoc gateway kiem han theo gio that nen test ky token theo gio that, khong dung Clock.fixed
    private final Clock clock = Clock.systemUTC();
    private final RSAKey signingKey;
    private final HttpServer server;

    private TestJwtIssuer(RSAKey signingKey, HttpServer server) {
        this.signingKey = signingKey;
        this.server = server;
    }

    public static TestJwtIssuer start() {
        try {
            var key = new RSAKeyGenerator(RSA_KEY_SIZE).keyID(KEY_ID).generate();
            var jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext(JWKS_PATH, exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (var body = exchange.getResponseBody()) {
                    body.write(jwks);
                }
            });
            server.start();
            return new TestJwtIssuer(key, server);
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot generate RSA key", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start JWKS server", e);
        }
    }

    public String jwkSetUri() {
        return "http://localhost:" + server.getAddress().getPort() + JWKS_PATH;
    }

    /** Token hop le cua customer01: role CUSTOMER, claim cif. */
    public String customerToken() {
        return customerToken(CUSTOMER_SUBJECT);
    }

    /** Token hop le cua mot customer khac voi user id (claim {@code sub}) cho truoc. */
    public String customerToken(String subject) {
        return sign(customerClaims(clock.instant().plus(TOKEN_LIFESPAN), GATEWAY_AUDIENCE, subject));
    }

    public String expiredCustomerToken() {
        var expiredAt = clock.instant().minus(Duration.ofMinutes(10));
        return sign(customerClaims(expiredAt, GATEWAY_AUDIENCE, CUSTOMER_SUBJECT));
    }

    public String customerTokenForAudience(String audience) {
        return sign(customerClaims(clock.instant().plus(TOKEN_LIFESPAN), audience, CUSTOMER_SUBJECT));
    }

    private JWTClaimsSet customerClaims(Instant expiresAt, String audience, String subject) {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(audience)
                .subject(subject)
                .issueTime(Date.from(expiresAt.minus(TOKEN_LIFESPAN)))
                .expirationTime(Date.from(expiresAt))
                .claim("cif", CUSTOMER_CIF)
                .claim("realm_access", Map.of("roles", List.of(CUSTOMER_ROLE)))
                .build();
    }

    private String sign(JWTClaimsSet claims) {
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build();
        var jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot sign JWT", e);
        }
        return jwt.serialize();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
