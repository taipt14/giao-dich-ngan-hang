package vn.bank.common.test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Base class cho integration test can Oracle, Kafka, Redis that.
 *
 * <p>Container la singleton: khoi dong mot lan khi class duoc nap va dung chung cho moi test class trong cung JVM
 * (moi module mot JVM khi chay {@code mvn verify}). Khong dung {@code @Testcontainers}/{@code @Container} vi
 * extension do dung container sau moi class. Container khong duoc dung tuong minh: Ryuk cua Testcontainers don khi
 * JVM ket thuc.
 *
 * <p>Container bat {@code withReuse(true)}: chi co tac dung khi may dev bat {@code testcontainers.reuse.enable=true}
 * trong {@code ~/.testcontainers.properties} (xem {@code infra/README.md}). Khi do container song qua nhieu lan chay
 * va du lieu (Oracle, topic Kafka, key Redis) con lai tu lan truoc, nen test phai tu tao du lieu rieng.
 *
 * <p>Oracle chay dung script {@code infra/oracle/init/01_create_service_schemas.sh} cua docker-compose, nen co user
 * cua moi schema khai bao trong script, cung mat khau {@link #SERVICE_DB_PASSWORD}. Base class chi nap URL va mat
 * khau datasource, <b>khong</b> nap username: service van dang nhap bang username khai bao trong
 * {@code application.yml} cua no, nen test kiem tra duoc service dung dung schema cua minh.
 */
public abstract class AbstractIntegrationTest {

    /** Mat khau test cua moi user schema service trong container Oracle. */
    public static final String SERVICE_DB_PASSWORD = "test";

    // Cung image voi infra/docker-compose.yml de test chay tren dung phien ban
    private static final String ORACLE_IMAGE = "gvenzl/oracle-free:23.6-slim-faststart";
    private static final String KAFKA_IMAGE = "apache/kafka:3.9.1";
    private static final String REDIS_IMAGE = "redis:7.4-alpine";
    private static final int REDIS_PORT = 6379;

    private static final String ORACLE_INIT_DIR = "/container-entrypoint-initdb.d/";
    private static final int INIT_SCRIPT_FILE_MODE = 0755;
    private static final ServiceSchemaInitScript INIT_SCRIPT = ServiceSchemaInitScript.fromRepoRoot();

    /** Schema service co trong container Oracle, doc tu script init. */
    protected static final List<String> SERVICE_SCHEMAS = INIT_SCRIPT.schemas();

    protected static final OracleContainer ORACLE = new OracleContainer(ORACLE_IMAGE)
            .withPassword(SERVICE_DB_PASSWORD)
            .withEnv(schemaPasswordVariables())
            .withCopyFileToContainer(
                    MountableFile.forHostPath(INIT_SCRIPT.path(), INIT_SCRIPT_FILE_MODE),
                    ORACLE_INIT_DIR + INIT_SCRIPT.path().getFileName())
            .withReuse(true);

    protected static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE).withReuse(true);

    protected static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
            .withExposedPorts(REDIS_PORT)
            .withReuse(true);

    static {
        // Khoi dong song song de giam thoi gian cho
        Startables.deepStart(ORACLE, KAFKA, REDIS).join();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
        registry.add("spring.datasource.password", () -> SERVICE_DB_PASSWORD);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    }

    /** Tao consumer doc {@code topic} tren Kafka container dung chung. Phai dong consumer sau khi dung. */
    protected static KafkaTestConsumer createKafkaTestConsumer(String topic) {
        return KafkaTestConsumer.subscribe(KAFKA.getBootstrapServers(), topic);
    }

    // Script init doc mat khau schema X tu bien X_DB_PASSWORD, thieu bien nao la dung khoi tao
    private static Map<String, String> schemaPasswordVariables() {
        return SERVICE_SCHEMAS.stream()
                .collect(Collectors.toMap(ServiceSchemaInitScript::passwordVariableOf, schema -> SERVICE_DB_PASSWORD));
    }
}
