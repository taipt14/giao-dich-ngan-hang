package vn.bank.customer.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import vn.bank.common.error.ErrorResponse;
import vn.bank.common.events.EventJson;
import vn.bank.common.events.Topics;
import vn.bank.common.test.AbstractIntegrationTest;
import vn.bank.customer.domain.CustomerStatus;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CustomerControllerIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-21T03:00:00Z");
    private static final String CUSTOMERS_PATH = "/api/v1/customers";
    private static final String ROLES_HEADER = "X-Roles";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ADULT_BIRTH_DATE = "1990-05-15";
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(30);

    @LocalServerPort
    private int port;

    private RestClient client;

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @BeforeEach
    void createClient() {
        // Khong nem exception voi 4xx/5xx de test doc duoc body loi
        client = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
                .build();
    }

    @Test
    void should_return201WithCif_when_adminRegistersCustomer() {
        // given
        var idNumber = randomDigits(12);
        var body = requestBody(idNumber, randomPhoneNumber(), ADULT_BIRTH_DATE);

        // when
        var response = register(body, "ADMIN", CustomerResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var customer = response.getBody();
        assertThat(customer).isNotNull();
        assertThat(customer.cif()).matches("C\\d{9}");
        assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(customer.createdAt()).isEqualTo(NOW);
    }

    @Test
    void should_returnMaskedIdNumber_when_customerRegistered() {
        // given
        var idNumber = randomDigits(12);
        var body = requestBody(idNumber, randomPhoneNumber(), ADULT_BIRTH_DATE);

        // when
        var response = register(body, "ADMIN", String.class);

        // then
        var masked = idNumber.substring(0, 4) + "******" + idNumber.substring(10);
        assertThat(response.getBody())
                .contains("\"idNumber\":\"" + masked + "\"")
                .doesNotContain(idNumber);
    }

    @Test
    void should_return409IdNumberDuplicated_when_idNumberAlreadyRegistered() {
        // given
        var idNumber = randomDigits(12);
        register(requestBody(idNumber, randomPhoneNumber(), ADULT_BIRTH_DATE), "ADMIN", CustomerResponse.class);

        // when
        var response =
                register(requestBody(idNumber, randomPhoneNumber(), ADULT_BIRTH_DATE), "ADMIN", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.CONFLICT, "CUSTOMER_ID_NUMBER_DUPLICATED");
    }

    @Test
    void should_return409PhoneDuplicated_when_phoneNumberAlreadyRegistered() {
        // given
        var phoneNumber = randomPhoneNumber();
        register(requestBody(randomDigits(12), phoneNumber, ADULT_BIRTH_DATE), "ADMIN", CustomerResponse.class);

        // when
        var response =
                register(requestBody(randomDigits(12), phoneNumber, ADULT_BIRTH_DATE), "ADMIN", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.CONFLICT, "CUSTOMER_PHONE_DUPLICATED");
    }

    @Test
    void should_return400Underage_when_customerUnder18() {
        // given: ngay nghiep vu 2026-11-21, sinh 2008-11-22 thi con 1 ngay nua moi du 18 tuoi
        var body = requestBody(randomDigits(12), randomPhoneNumber(), "2008-11-22");

        // when
        var response = register(body, "ADMIN", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "CUSTOMER_UNDERAGE");
    }

    @Test
    void should_return403_when_customerRoleCallsApi() {
        // given
        var body = requestBody(randomDigits(12), randomPhoneNumber(), ADULT_BIRTH_DATE);

        // when
        var response = register(body, "CUSTOMER", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void should_return403_when_rolesHeaderMissing() {
        // given
        var body = requestBody(randomDigits(12), randomPhoneNumber(), ADULT_BIRTH_DATE);

        // when
        var response = client.post()
                .uri(CUSTOMERS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void should_returnValidationFailed_when_idNumberNot12Digits() {
        // given
        var body = requestBody("12345", randomPhoneNumber(), ADULT_BIRTH_DATE);

        // when
        var response = register(body, "ADMIN", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(response.getBody().timestamp()).isEqualTo(NOW);
        assertThat(response.getBody().details())
                .anySatisfy(detail -> assertThat(detail.field()).isEqualTo("idNumber"));
    }

    @Test
    void should_returnValidationFailed_when_phoneNumberNotVietnameseFormat() {
        // given
        var body = requestBody(randomDigits(12), "84912345678", ADULT_BIRTH_DATE);

        // when
        var response = register(body, "ADMIN", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(response.getBody().details())
                .anySatisfy(detail -> assertThat(detail.field()).isEqualTo("phoneNumber"));
    }

    @Test
    void should_publishCustomerCreatedEvent_when_customerRegistered() {
        // given
        var phoneNumber = randomPhoneNumber();
        var body = requestBody(randomDigits(12), phoneNumber, ADULT_BIRTH_DATE);

        try (var consumer = createKafkaTestConsumer(Topics.CUSTOMER_EVENTS)) {
            // when
            var cif = register(body, "ADMIN", CustomerResponse.class).getBody().cif();

            // then
            var event = EventJson.mapper()
                    .readTree(consumer.getRecordByKey(cif, EVENT_TIMEOUT).value());
            assertThat(event.get("eventType").asString()).isEqualTo("CustomerCreated");
            assertThat(event.get("source").asString()).isEqualTo("customer-service");
            assertThat(event.get("eventId").asString()).isNotBlank();
            assertThat(event.get("occurredAt").asString()).isEqualTo("2026-11-21T03:00:00Z");
            var payload = event.get("payload");
            assertThat(payload.get("cif").asString()).isEqualTo(cif);
            assertThat(payload.get("phoneNumber").asString()).isEqualTo(phoneNumber);
            assertThat(payload.get("status").asString()).isEqualTo("ACTIVE");
        }
    }

    private <T> ResponseEntity<T> register(String body, String roles, Class<T> responseType) {
        return client.post()
                .uri(CUSTOMERS_PATH)
                .header(USER_ID_HEADER, "test-user")
                .header(ROLES_HEADER, roles)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(responseType);
    }

    private static void assertError(ResponseEntity<ErrorResponse> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(code);
    }

    private static String requestBody(String idNumber, String phoneNumber, String dateOfBirth) {
        return """
                {"fullName":"Nguyen Van A","dateOfBirth":"%s","idNumber":"%s","phoneNumber":"%s",
                 "email":"a@example.com","address":"1 Ly Thai To, Ha Noi"}
                """.formatted(dateOfBirth, idNumber, phoneNumber);
    }

    // Container Oracle co the duoc dung lai giua cac lan chay: moi test tu sinh CCCD, so dien thoai rieng
    private static String randomDigits(int length) {
        var builder = new StringBuilder(length);
        for (var i = 0; i < length; i++) {
            builder.append(ThreadLocalRandom.current().nextInt(10));
        }
        return builder.toString();
    }

    private static String randomPhoneNumber() {
        return "09" + randomDigits(8);
    }
}
