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

/** Tra cuu, cap nhat thong tin lien he, doi trang thai khach hang va API noi bo (BANK-014). */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CustomerManagementControllerIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-21T03:00:00Z");
    private static final String CUSTOMERS_PATH = "/api/v1/customers";
    private static final String INTERNAL_CUSTOMERS_PATH = "/internal/customers";
    private static final String ROLES_HEADER = "X-Roles";
    private static final String CIF_HEADER = "X-Cif";
    private static final String ADMIN = "ADMIN";
    private static final String CUSTOMER = "CUSTOMER";
    private static final String UNKNOWN_CIF = "C999999998";
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
    void should_returnOwnProfile_when_customerGetsMe() {
        // given
        var cif = registerCustomer(randomPhoneNumber());

        // when
        var response = client.get()
                .uri(CUSTOMERS_PATH + "/me")
                .header(ROLES_HEADER, CUSTOMER)
                .header(CIF_HEADER, cif)
                .retrieve()
                .toEntity(CustomerResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().cif()).isEqualTo(cif);
        assertThat(response.getBody().idNumber()).contains("******");
    }

    @Test
    void should_return403_when_customerGetsOtherCustomerCif() {
        // given
        var ownCif = registerCustomer(randomPhoneNumber());
        var otherCif = registerCustomer(randomPhoneNumber());

        // when
        var response = client.get()
                .uri(CUSTOMERS_PATH + "/{cif}", otherCif)
                .header(ROLES_HEADER, CUSTOMER)
                .header(CIF_HEADER, ownCif)
                .retrieve()
                .toEntity(ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void should_return403_when_meCalledWithoutCifHeader() {
        // when
        var response = client.get()
                .uri(CUSTOMERS_PATH + "/me")
                .header(ROLES_HEADER, CUSTOMER)
                .retrieve()
                .toEntity(ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void should_returnCustomer_when_adminGetsByCif() {
        // given
        var cif = registerCustomer(randomPhoneNumber());

        // when
        var response = getAsAdmin(cif, CustomerResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().cif()).isEqualTo(cif);
        assertThat(response.getBody().status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void should_return404CustomerNotFound_when_cifUnknown() {
        // when
        var response = getAsAdmin(UNKNOWN_CIF, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND");
    }

    @Test
    void should_updateContactFields_when_adminPatchesPhoneEmailAddress() {
        // given
        var cif = registerCustomer(randomPhoneNumber());
        var newPhone = randomPhoneNumber();
        var body = """
                {"phoneNumber":"%s","email":"new@example.com","address":"2 Trang Tien, Ha Noi"}
                """.formatted(newPhone);

        // when
        var response = patch(cif, body, ADMIN, CustomerResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var customer = getAsAdmin(cif, CustomerResponse.class).getBody();
        assertThat(customer.phoneNumber()).isEqualTo(newPhone);
        assertThat(customer.email()).isEqualTo("new@example.com");
        assertThat(customer.address()).isEqualTo("2 Trang Tien, Ha Noi");
        assertThat(customer.fullName()).isEqualTo("Nguyen Van A");
    }

    @Test
    void should_return400_when_patchChangesFullName() {
        // given
        var cif = registerCustomer(randomPhoneNumber());

        // when
        var response = patch(cif, "{\"fullName\":\"Tran Thi B\"}", ADMIN, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(response.getBody().details())
                .anySatisfy(detail -> assertThat(detail.field()).isEqualTo("fullName"));
        assertThat(getAsAdmin(cif, CustomerResponse.class).getBody().fullName()).isEqualTo("Nguyen Van A");
    }

    @Test
    void should_return400_when_patchChangesIdNumber() {
        // given
        var cif = registerCustomer(randomPhoneNumber());

        // when
        var response = patch(cif, "{\"idNumber\":\"001234567889\"}", ADMIN, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(response.getBody().details())
                .anySatisfy(detail -> assertThat(detail.field()).isEqualTo("idNumber"));
    }

    @Test
    void should_return409PhoneDuplicated_when_patchUsesPhoneOfOtherCustomer() {
        // given
        var takenPhone = randomPhoneNumber();
        registerCustomer(takenPhone);
        var cif = registerCustomer(randomPhoneNumber());

        // when
        var response = patch(cif, "{\"phoneNumber\":\"%s\"}".formatted(takenPhone), ADMIN, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.CONFLICT, "CUSTOMER_PHONE_DUPLICATED");
    }

    @Test
    void should_return403_when_customerPatchesCustomer() {
        // given
        var cif = registerCustomer(randomPhoneNumber());

        // when
        var response = patch(cif, "{\"email\":\"new@example.com\"}", CUSTOMER, ErrorResponse.class);

        // then
        assertError(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void should_publishCustomerStatusChanged_when_adminLocksCustomer() {
        // given
        var cif = registerCustomer(randomPhoneNumber());

        try (var consumer = createKafkaTestConsumer(Topics.CUSTOMER_EVENTS)) {
            // when
            var response = changeStatus(cif, "LOCKED", "Nghi ngo gian lan", CustomerResponse.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().status()).isEqualTo(CustomerStatus.LOCKED);
            var record = consumer.getRecord(
                    candidate ->
                            cif.equals(candidate.key()) && candidate.value().contains("CustomerStatusChanged"),
                    EVENT_TIMEOUT);
            var event = EventJson.mapper().readTree(record.value());
            assertThat(event.get("source").asString()).isEqualTo("customer-service");
            assertThat(event.get("occurredAt").asString()).isEqualTo("2026-11-21T03:00:00Z");
            var payload = event.get("payload");
            assertThat(payload.get("cif").asString()).isEqualTo(cif);
            assertThat(payload.get("previousStatus").asString()).isEqualTo("ACTIVE");
            assertThat(payload.get("status").asString()).isEqualTo("LOCKED");
            assertThat(payload.get("reason").asString()).isEqualTo("Nghi ngo gian lan");
            assertThat(payload.get("changedAt").asString()).isEqualTo("2026-11-21T03:00:00Z");
        }
    }

    @Test
    void should_unlockCustomer_when_adminChangesLockedToActive() {
        // given
        var cif = registerCustomer(randomPhoneNumber());
        changeStatus(cif, "LOCKED", "Nghi ngo gian lan", CustomerResponse.class);

        // when
        var response = changeStatus(cif, "ACTIVE", "Da xac minh chinh chu", CustomerResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void should_return400_when_statusChangeHasNoReason() {
        // given
        var cif = registerCustomer(randomPhoneNumber());

        // when
        var response = changeStatus(cif, "LOCKED", " ", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(response.getBody().details())
                .anySatisfy(detail -> assertThat(detail.field()).isEqualTo("reason"));
    }

    @Test
    void should_return422StatusUnchanged_when_customerAlreadyInTargetStatus() {
        // given
        var cif = registerCustomer(randomPhoneNumber());

        // when
        var response = changeStatus(cif, "ACTIVE", "Mo lai", ErrorResponse.class);

        // then
        assertError(response, HttpStatus.UNPROCESSABLE_CONTENT, "CUSTOMER_STATUS_UNCHANGED");
    }

    @Test
    void should_returnCifAndStatus_when_internalApiCalledWithoutRoles() {
        // given
        var cif = registerCustomer(randomPhoneNumber());
        changeStatus(cif, "LOCKED", "Nghi ngo gian lan", CustomerResponse.class);

        // when
        var response = client.get()
                .uri(INTERNAL_CUSTOMERS_PATH + "/{cif}", cif)
                .retrieve()
                .toEntity(CustomerStatusResponse.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new CustomerStatusResponse(cif, CustomerStatus.LOCKED));
    }

    @Test
    void should_return404_when_internalApiCalledWithUnknownCif() {
        // when
        var response = client.get()
                .uri(INTERNAL_CUSTOMERS_PATH + "/{cif}", UNKNOWN_CIF)
                .retrieve()
                .toEntity(ErrorResponse.class);

        // then
        assertError(response, HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND");
    }

    private String registerCustomer(String phoneNumber) {
        var body = """
                {"fullName":"Nguyen Van A","dateOfBirth":"1990-05-15","idNumber":"%s","phoneNumber":"%s",
                 "email":"a@example.com","address":"1 Ly Thai To, Ha Noi"}
                """.formatted(randomDigits(12), phoneNumber);
        var response = client.post()
                .uri(CUSTOMERS_PATH)
                .header(ROLES_HEADER, ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(CustomerResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().cif();
    }

    private <T> ResponseEntity<T> getAsAdmin(String cif, Class<T> responseType) {
        return client.get()
                .uri(CUSTOMERS_PATH + "/{cif}", cif)
                .header(ROLES_HEADER, ADMIN)
                .retrieve()
                .toEntity(responseType);
    }

    private <T> ResponseEntity<T> patch(String cif, String body, String roles, Class<T> responseType) {
        return client.patch()
                .uri(CUSTOMERS_PATH + "/{cif}", cif)
                .header(ROLES_HEADER, roles)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(responseType);
    }

    private <T> ResponseEntity<T> changeStatus(String cif, String status, String reason, Class<T> responseType) {
        return client.post()
                .uri(CUSTOMERS_PATH + "/{cif}/status", cif)
                .header(ROLES_HEADER, ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":\"%s\",\"reason\":\"%s\"}".formatted(status, reason))
                .retrieve()
                .toEntity(responseType);
    }

    private static void assertError(ResponseEntity<ErrorResponse> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status.value());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(code);
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
