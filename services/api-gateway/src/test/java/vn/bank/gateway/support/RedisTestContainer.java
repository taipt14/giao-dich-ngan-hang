package vn.bank.gateway.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis that cho integration test rate limit. Gateway khong co DB nen khong dung {@code AbstractIntegrationTest} (khoi
 * ca Oracle, Kafka): chi khoi Redis, cung image voi infra/docker-compose.yml.
 *
 * <p>Container la singleton, khoi dong mot lan khi class duoc nap va dung chung trong JVM; Ryuk cua Testcontainers don
 * khi JVM ket thuc. Key Redis con lai giua cac test nen moi test tu dung user id rieng.
 */
public final class RedisTestContainer {

    private static final String REDIS_IMAGE = "redis:7.4-alpine";
    private static final int REDIS_PORT = 6379;

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE)).withExposedPorts(REDIS_PORT);

    static {
        REDIS.start();
    }

    private RedisTestContainer() {}

    public static String host() {
        return REDIS.getHost();
    }

    public static int port() {
        return REDIS.getMappedPort(REDIS_PORT);
    }
}
