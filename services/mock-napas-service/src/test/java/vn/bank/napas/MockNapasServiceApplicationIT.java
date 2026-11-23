package vn.bank.napas;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import vn.bank.common.test.AbstractIntegrationTest;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class MockNapasServiceApplicationIT extends AbstractIntegrationTest {

    private static final String SERVICE_SCHEMA = "NAPAS_SVC";

    private final JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    MockNapasServiceApplicationIT(@Autowired JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

    // Container co user cua moi schema voi cung mat khau, nen service cau hinh nham username van ket noi duoc:
    // chi assert nay bat duoc loi do
    @Test
    void should_connectAsOwnSchemaUser_when_serviceStarted() {
        // given: username tu application.yml, URL va mat khau tu AbstractIntegrationTest

        // when
        var sessionUser = jdbcTemplate.queryForObject("select user from dual", String.class);
        var currentSchema =
                jdbcTemplate.queryForObject("select sys_context('USERENV', 'CURRENT_SCHEMA') from dual", String.class);

        // then
        assertThat(sessionUser).isEqualTo(SERVICE_SCHEMA);
        assertThat(currentSchema).isEqualTo(SERVICE_SCHEMA);
    }
}
