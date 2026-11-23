package vn.bank.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.bank.common.events.EventJson;
import vn.bank.common.events.Topics;
import vn.bank.common.test.AbstractIntegrationTest;
import vn.bank.customer.domain.CustomerProfile;
import vn.bank.customer.domain.CustomerStatus;
import vn.bank.customer.infrastructure.persistence.CustomerRepository;

/**
 * Outbox cua customer-service (BANK-046) voi Oracle, Kafka that: event chi duoc gui khi transaction nghiep vu commit.
 * Publisher chay moi 1 giay, nen khong thay event sau vai giay nghia la event khong bao gio duoc ghi.
 */
@SpringBootTest
class CustomerOutboxIT extends AbstractIntegrationTest {

    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(30);
    // Nhieu lan chu ky publisher (1 giay): neu event da commit thi chac chan da duoc gui trong khoang nay
    private static final Duration NO_EVENT_WAIT = Duration.ofSeconds(4);

    private final RegisterCustomerService registerCustomerService;
    private final ChangeCustomerStatusService changeCustomerStatusService;
    private final CustomerRepository customerRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    CustomerOutboxIT(
            @Autowired RegisterCustomerService registerCustomerService,
            @Autowired ChangeCustomerStatusService changeCustomerStatusService,
            @Autowired CustomerRepository customerRepository,
            @Autowired JdbcTemplate jdbcTemplate,
            @Autowired PlatformTransactionManager transactionManager) {
        this.registerCustomerService = registerCustomerService;
        this.changeCustomerStatusService = changeCustomerStatusService;
        this.customerRepository = customerRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void should_markCustomerCreatedSent_when_registrationCommitted() {
        // given
        var profile = randomProfile();

        // when
        var cif = registerCustomerService.register(profile).getCif();

        // then
        await().atMost(EVENT_TIMEOUT).until(() -> "SENT".equals(outboxStatusOf(cif, "CustomerCreated")));
    }

    @Test
    void should_notPublishCustomerCreated_when_transactionRolledBack() {
        // given
        var profile = randomProfile();

        try (var consumer = createKafkaTestConsumer(Topics.CUSTOMER_EVENTS)) {
            // when
            var cif = transactionTemplate.execute(status -> {
                var customer = registerCustomerService.register(profile);
                status.setRollbackOnly();
                return customer.getCif();
            });

            // then
            assertThat(customerRepository.existsById(cif)).isFalse();
            assertThat(countOutboxRowsOf(cif)).isZero();
            assertThat(consumer.findRecord(consumerRecord -> cif.equals(consumerRecord.key()), NO_EVENT_WAIT))
                    .isEmpty();
        }
    }

    @Test
    void should_notPublishCustomerStatusChanged_when_transactionRolledBack() {
        // given
        var cif = registerCustomerService.register(randomProfile()).getCif();

        try (var consumer = createKafkaTestConsumer(Topics.CUSTOMER_EVENTS)) {
            // when
            transactionTemplate.executeWithoutResult(status -> {
                changeCustomerStatusService.changeStatus(cif, CustomerStatus.LOCKED, "Nghi ngo gian lan");
                status.setRollbackOnly();
            });

            // then
            assertThat(customerRepository.findById(cif).orElseThrow().getStatus())
                    .isEqualTo(CustomerStatus.ACTIVE);
            assertThat(outboxStatusOf(cif, "CustomerStatusChanged")).isNull();
            var statusChanged = consumer.findRecord(
                    consumerRecord -> cif.equals(consumerRecord.key())
                            && "CustomerStatusChanged"
                                    .equals(EventJson.mapper()
                                            .readTree(consumerRecord.value())
                                            .get("eventType")
                                            .asString()),
                    NO_EVENT_WAIT);
            assertThat(statusChanged).isEmpty();
        }
    }

    private int countOutboxRowsOf(String cif) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM OUTBOX_EVENT WHERE AGGREGATE_ID = ?", Integer.class, cif);
    }

    private String outboxStatusOf(String cif, String eventType) {
        var statuses = jdbcTemplate.queryForList(
                "SELECT STATUS FROM OUTBOX_EVENT WHERE AGGREGATE_ID = ? AND EVENT_TYPE = ?",
                String.class,
                cif,
                eventType);
        return statuses.isEmpty() ? null : statuses.getFirst();
    }

    // Container Oracle co the duoc dung lai giua cac lan chay: tu sinh CCCD, so dien thoai rieng
    private static CustomerProfile randomProfile() {
        var random = ThreadLocalRandom.current();
        return new CustomerProfile(
                "Nguyen Van A",
                LocalDate.of(1990, 5, 15),
                "%012d".formatted(random.nextLong(1_000_000_000_000L)),
                "09%08d".formatted(random.nextInt(100_000_000)),
                "a@example.com",
                "1 Ly Thai To, Ha Noi");
    }
}
