package vn.bank.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.bank.common.test.AbstractIntegrationTest;

/** Test mau: integration test cua service chi can ke thua {@link AbstractIntegrationTest}. */
@SpringBootTest
class AccountServiceIntegrationSampleIT extends AbstractIntegrationTest {

    private static final String SERVICE_SCHEMA = "ACCOUNT_SVC";
    private static final int REDIS_PORT = 6379;
    private static final String SAMPLE_TOPIC = "account-service.sample-it";
    private static final Duration RECORD_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration NO_RECORD_TIMEOUT = Duration.ofSeconds(2);

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    AccountServiceIntegrationSampleIT(@Autowired JdbcTemplate jdbcTemplate, @Autowired Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @Test
    void should_connectAsOwnSchemaUserAndRunFlyway_when_extendingAbstractIntegrationTest() {
        // given: datasource URL/password tu base class, username tu application.yml

        // when
        var sessionUser = jdbcTemplate.queryForObject("select user from dual", String.class);
        var baselineApplied = jdbcTemplate.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"version\" = '1' and \"success\" = 1",
                Integer.class);

        // then
        assertThat(sessionUser).isEqualTo(SERVICE_SCHEMA);
        assertThat(baselineApplied).isEqualTo(1);
    }

    @Test
    void should_createUserOfEverySchemaInInitScript_when_oracleContainerStarted() {
        // given: SERVICE_SCHEMAS doc tu infra/oracle/init/01_create_service_schemas.sh

        // when
        var users = jdbcTemplate.queryForList("select username from all_users", String.class);

        // then
        assertThat(users).containsAll(SERVICE_SCHEMAS);
    }

    @Test
    void should_exposeKafkaAndRedisProperties_when_extendingAbstractIntegrationTest() throws Exception {
        // given: container Kafka, Redis dung chung

        // when
        var bootstrapServers = environment.getProperty("spring.kafka.bootstrap-servers");
        var redisPort = environment.getProperty("spring.data.redis.port", Integer.class);
        var redisPing = REDIS.execInContainer("redis-cli", "ping");

        // then
        assertThat(bootstrapServers).isEqualTo(KAFKA.getBootstrapServers());
        assertThat(redisPort).isEqualTo(REDIS.getMappedPort(REDIS_PORT));
        assertThat(redisPing.getStdout()).contains("PONG");
    }

    @Test
    void should_receivePublishedRecord_when_kafkaTestConsumerReadsTopic() throws Exception {
        // given
        var key = UUID.randomUUID().toString();
        sendRecord(key, "{\"sample\":true}");

        // when
        try (var consumer = createKafkaTestConsumer(SAMPLE_TOPIC)) {
            var received = consumer.getRecordByKey(key, RECORD_TIMEOUT);

            // then
            assertThat(received.value()).isEqualTo("{\"sample\":true}");
        }
    }

    @Test
    void should_returnEmpty_when_noMatchingRecordWithinTimeout() throws Exception {
        // given: topic ton tai nhung khong co message voi key can tim
        sendRecord(UUID.randomUUID().toString(), "{}");
        var missingKey = UUID.randomUUID().toString();

        // when
        try (var consumer = createKafkaTestConsumer(SAMPLE_TOPIC)) {
            var found =
                    consumer.findRecord(consumerRecord -> missingKey.equals(consumerRecord.key()), NO_RECORD_TIMEOUT);

            // then
            assertThat(found).isEmpty();
        }
    }

    @Test
    void should_reuseContainersOfFirstTestClass_when_runAfterAnotherClassInSameJvm() {
        // given
        var current = SharedContainerFingerprints.fingerprintsOf(ORACLE, KAFKA, REDIS);

        // when
        var firstSeen = SharedContainerFingerprints.firstSeenOrRecord(current);

        // then
        assertThat(current).isEqualTo(firstSeen);
        assertThat(ORACLE.isRunning() && KAFKA.isRunning() && REDIS.isRunning()).isTrue();
    }

    private static void sendRecord(String key, String value) throws Exception {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (var producer = new KafkaProducer<String, String>(config)) {
            producer.send(new ProducerRecord<>(SAMPLE_TOPIC, key, value)).get();
        }
    }
}
