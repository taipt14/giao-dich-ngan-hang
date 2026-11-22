package vn.bank.common.test;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class cho integration test can Oracle, Kafka, Redis that.
 *
 * <p>Container la singleton: khoi dong mot lan khi class duoc nap va dung chung cho moi test class trong cung JVM
 * (moi module mot JVM khi chay {@code mvn verify}). Khong dung {@code @Testcontainers}/{@code @Container} vi
 * extension do dung container sau moi class. Container khong duoc dung tuong minh: Ryuk cua Testcontainers don khi
 * JVM ket thuc.
 *
 * <p>Oracle tao san user/schema cua ca 6 service voi cung mat khau {@link #SERVICE_DB_PASSWORD}. Base class chi nap
 * URL va mat khau datasource, <b>khong</b> nap username: service van dang nhap bang username khai bao trong
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

    // Giong danh sach trong infra/oracle/init/01_create_service_schemas.sh
    private static final List<String> SERVICE_SCHEMAS =
            List.of("CUSTOMER_SVC", "ACCOUNT_SVC", "TRANSFER_SVC", "NAPAS_SVC", "NOTI_SVC", "FRAUD_SVC");
    private static final String ORACLE_INIT_SCRIPT = "/container-entrypoint-initdb.d/01_create_service_schemas.sql";

    protected static final OracleContainer ORACLE = new OracleContainer(ORACLE_IMAGE)
            .withPassword(SERVICE_DB_PASSWORD)
            .withCopyToContainer(Transferable.of(createServiceSchemasScript()), ORACLE_INIT_SCRIPT);

    protected static final KafkaContainer KAFKA = new KafkaContainer(KAFKA_IMAGE);

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE)).withExposedPorts(REDIS_PORT);

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

    // Script SQL*Plus chay bang SYS (o CDB root) khi container khoi tao lan dau, nen phai chuyen sang PDB
    private static String createServiceSchemasScript() {
        var createUsers = SERVICE_SCHEMAS.stream()
                .map(schema -> """
                        CREATE USER %1$s IDENTIFIED BY "%2$s" DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS;
                        GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW,
                              CREATE PROCEDURE, CREATE TRIGGER, CREATE SYNONYM TO %1$s;
                        """.formatted(schema, SERVICE_DB_PASSWORD))
                .collect(Collectors.joining());
        return """
                WHENEVER SQLERROR EXIT FAILURE
                ALTER SESSION SET CONTAINER = FREEPDB1;
                %sEXIT
                """.formatted(createUsers);
    }
}
