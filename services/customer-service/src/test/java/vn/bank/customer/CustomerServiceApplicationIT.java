package vn.bank.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CustomerServiceApplicationIT {

    @LocalServerPort
    private int port;

    @Test
    void should_startAndReportHealthUp_when_applicationLaunched() {
        // given
        var client = RestClient.create("http://localhost:" + port);

        // when
        var body = client.get().uri("/actuator/health").retrieve().body(String.class);

        // then
        assertThat(body).contains("\"status\":\"UP\"");
    }
}
