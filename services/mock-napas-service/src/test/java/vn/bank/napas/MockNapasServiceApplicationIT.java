package vn.bank.napas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class MockNapasServiceApplicationIT {

    private static final String SERVICE_SCHEMA = "NAPAS_SVC";
    private static final String ORACLE_IMAGE = "gvenzl/oracle-free:23.6-slim-faststart";

    // Container chi co user SERVICE_SCHEMA; username lay tu application.yml (khong override)
    // nen neu service cau hinh user cua schema khac thi se khong ket noi duoc
    @Container
    static final OracleContainer ORACLE = new OracleContainer(ORACLE_IMAGE).withUsername(SERVICE_SCHEMA);

    private final JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    MockNapasServiceApplicationIT(@Autowired JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @DynamicPropertySource
    static void oracleProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
        registry.add("spring.datasource.password", ORACLE::getPassword);
    }

    @Test
    void should_startAndReportHealthUp_when_applicationLaunched() {
        // given
        var client = RestClient.create("http://localhost:" + port);

        // when
        var body = client.get().uri("/actuator/health").retrieve().body(String.class);

        // then
        assertThat(body).contains("\"status\":\"UP\"");
    }

    @Test
    void should_createFlywayHistoryInOwnSchema_when_serviceStarted() {
        // given: Flyway da chay khi context khoi dong

        // when
        var historyOwners = jdbcTemplate.queryForList(
                "select owner from all_tables where table_name = 'flyway_schema_history'", String.class);
        var baselineApplied = jdbcTemplate.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"version\" = '1' and \"success\" = 1",
                Integer.class);
        var processedMessageOwners = jdbcTemplate.queryForList(
                "select owner from all_tables where table_name = 'PROCESSED_MESSAGE'", String.class);

        // then
        assertThat(historyOwners).containsExactly(SERVICE_SCHEMA);
        assertThat(baselineApplied).isEqualTo(1);
        assertThat(processedMessageOwners).containsExactly(SERVICE_SCHEMA);
    }

    @Test
    void should_connectAsOwnSchemaUser_when_serviceStarted() {
        // given: datasource cau hinh trong application.yml

        // when
        var sessionUser = jdbcTemplate.queryForObject("select user from dual", String.class);
        var currentSchema = jdbcTemplate.queryForObject(
                "select sys_context('USERENV', 'CURRENT_SCHEMA') from dual", String.class);

        // then
        assertThat(sessionUser).isEqualTo(SERVICE_SCHEMA);
        assertThat(currentSchema).isEqualTo(SERVICE_SCHEMA);
    }
}
