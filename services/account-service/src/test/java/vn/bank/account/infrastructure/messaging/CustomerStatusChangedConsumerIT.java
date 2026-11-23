package vn.bank.account.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import vn.bank.common.events.EventEnvelope;
import vn.bank.common.events.Topics;
import vn.bank.common.events.customer.CustomerCreated;
import vn.bank.common.events.customer.CustomerStatusChanged;
import vn.bank.common.test.AbstractIntegrationTest;

@SpringBootTest
class CustomerStatusChangedConsumerIT extends AbstractIntegrationTest {

    // Lan dau group account-service join co the phai doc lai toan bo topic dung chung
    private static final Duration CONSUME_TIMEOUT = Duration.ofSeconds(60);
    private static final Instant NOW = Instant.parse("2026-11-22T03:00:00Z");
    private static final String SOURCE = "customer-service";

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    CustomerStatusChangedConsumerIT(
            @Autowired JdbcTemplate jdbcTemplate, @Autowired KafkaTemplate<String, Object> kafkaTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Test
    void should_lockAllActiveAccounts_when_customerLocked() {
        // given
        var cif = randomCif();
        var active1 = givenAccount(cif, "ACTIVE");
        var active2 = givenAccount(cif, "ACTIVE");
        var locked = givenAccount(cif, "LOCKED");
        var closed = givenAccount(cif, "CLOSED");
        var otherCustomerAccount = givenAccount(randomCif(), "ACTIVE");
        var event = statusChanged(cif, "LOCKED");

        // when
        send(cif, event);

        // then
        awaitProcessed(event.eventId());
        assertThat(statusOf(active1)).isEqualTo("LOCKED");
        assertThat(statusOf(active2)).isEqualTo("LOCKED");
        assertThat(statusOf(locked)).isEqualTo("LOCKED");
        assertThat(statusOf(closed)).isEqualTo("CLOSED");
        assertThat(statusOf(otherCustomerAccount)).isEqualTo("ACTIVE");
        assertThat(countHistoryOf(active1)).isEqualTo(1);
        assertThat(countHistoryOf(locked)).isZero();
        assertThat(countHistoryOf(closed)).isZero();
        assertThat(reasonOf(active1)).isEqualTo("Customer locked: Nghi ngo gian lan");
    }

    @Test
    void should_writeOneHistoryRow_when_sameEventReceivedTwice() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif, "ACTIVE");
        var event = statusChanged(cif, "LOCKED");
        // Cung key nen cung partition: marker duoc xu ly xong thi hai ban trung da xu ly xong
        var marker = statusChanged(cif, "ACTIVE");

        // when
        send(cif, event);
        send(cif, event);
        send(cif, marker);

        // then
        awaitProcessed(marker.eventId());
        assertThat(countHistoryOf(accountNo)).isEqualTo(1);
        assertThat(statusOf(accountNo)).isEqualTo("LOCKED");
        assertThat(countProcessed(event.eventId())).isEqualTo(1);
    }

    @Test
    void should_ignoreOtherEventTypes_when_consumingCustomerEvents() {
        // given
        var cif = randomCif();
        var accountNo = givenAccount(cif, "ACTIVE");
        var created = new EventEnvelope<>(
                UUID.randomUUID(),
                CustomerCreated.EVENT_TYPE,
                NOW,
                null,
                SOURCE,
                new CustomerCreated(cif, "Nguyen Van A", "0900000000", "a@example.com", "LOCKED", NOW));
        var marker = statusChanged(cif, "ACTIVE");

        // when
        send(cif, created);
        send(cif, marker);

        // then
        awaitProcessed(marker.eventId());
        assertThat(countProcessed(created.eventId())).isZero();
        assertThat(statusOf(accountNo)).isEqualTo("ACTIVE");
    }

    private void send(String cif, EventEnvelope<?> event) {
        kafkaTemplate.send(Topics.CUSTOMER_EVENTS, cif, event).join();
    }

    private void awaitProcessed(UUID eventId) {
        await().atMost(CONSUME_TIMEOUT).until(() -> countProcessed(eventId) > 0);
    }

    private static EventEnvelope<CustomerStatusChanged> statusChanged(String cif, String status) {
        var previous = "LOCKED".equals(status) ? "ACTIVE" : "LOCKED";
        return new EventEnvelope<>(
                UUID.randomUUID(),
                CustomerStatusChanged.EVENT_TYPE,
                NOW,
                null,
                SOURCE,
                new CustomerStatusChanged(cif, previous, status, "Nghi ngo gian lan", NOW));
    }

    private String givenAccount(String cif, String status) {
        var accountNo = randomAccountNo();
        jdbcTemplate.update("""
                INSERT INTO ACCOUNT (ACCOUNT_NO, CIF, ACCOUNT_TYPE, CURRENCY, BALANCE, AVAILABLE_BALANCE, STATUS,
                                     VERSION, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, 'CURRENT', 'VND', 0, 0, ?, 0, SYSTIMESTAMP, SYSTIMESTAMP)""", accountNo, cif, status);
        return accountNo;
    }

    private String statusOf(String accountNo) {
        return jdbcTemplate.queryForObject("SELECT STATUS FROM ACCOUNT WHERE ACCOUNT_NO = ?", String.class, accountNo);
    }

    private Integer countHistoryOf(String accountNo) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ACCOUNT_STATUS_HISTORY WHERE ACCOUNT_NO = ?", Integer.class, accountNo);
    }

    private String reasonOf(String accountNo) {
        return jdbcTemplate.queryForObject(
                "SELECT REASON FROM ACCOUNT_STATUS_HISTORY WHERE ACCOUNT_NO = ?", String.class, accountNo);
    }

    private Integer countProcessed(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PROCESSED_MESSAGE WHERE EVENT_ID = ?", Integer.class, eventId.toString());
    }

    // Container Oracle co the duoc dung lai giua cac lan chay: moi test tu sinh du lieu rieng.
    // Tien to 4000 de khong trung so tai khoan sinh tu sequence (1000...) va cua test khac
    private static String randomAccountNo() {
        return "4000%08d".formatted(ThreadLocalRandom.current().nextLong(1, 100_000_000L));
    }

    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }
}
