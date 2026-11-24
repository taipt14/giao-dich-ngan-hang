package vn.bank.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.bank.account.domain.AccountHolder;
import vn.bank.account.domain.AccountType;
import vn.bank.common.events.Topics;
import vn.bank.common.test.AbstractIntegrationTest;

/**
 * Outbox cua account-service (BANK-046) voi Oracle, Kafka that: event chi duoc gui khi transaction nghiep vu commit.
 * Publisher chay moi 1 giay, nen khong thay event sau vai giay nghia la event khong bao gio duoc ghi.
 */
@SpringBootTest
class AccountOutboxIT extends AbstractIntegrationTest {

    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(30);
    // Nhieu lan chu ky publisher (1 giay): neu event da commit thi chac chan da duoc gui trong khoang nay
    private static final Duration NO_EVENT_WAIT = Duration.ofSeconds(4);

    private final CreateAccountService createAccountService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    AccountOutboxIT(
            @Autowired CreateAccountService createAccountService,
            @Autowired JdbcTemplate jdbcTemplate,
            @Autowired PlatformTransactionManager transactionManager) {
        this.createAccountService = createAccountService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void should_markAccountOpenedSent_when_accountOpeningCommitted() {
        // given
        var holder = new AccountHolder(randomCif(), "ACTIVE");

        // when
        var accountNo = createAccountService.create(holder, AccountType.CURRENT).getAccountNo();

        // then
        await().atMost(EVENT_TIMEOUT).until(() -> "SENT".equals(outboxStatusOf(accountNo)));
    }

    @Test
    void should_notPublishAccountOpened_when_transactionRolledBack() {
        // given
        var holder = new AccountHolder(randomCif(), "ACTIVE");

        try (var consumer = createKafkaTestConsumer(Topics.ACCOUNT_EVENTS)) {
            // when
            var accountNo = transactionTemplate.execute(status -> {
                var account = createAccountService.create(holder, AccountType.CURRENT);
                status.setRollbackOnly();
                return account.getAccountNo();
            });

            // then
            assertThat(countAccounts(accountNo)).isZero();
            assertThat(outboxStatusOf(accountNo)).isNull();
            assertThat(consumer.findRecord(consumerRecord -> accountNo.equals(consumerRecord.key()), NO_EVENT_WAIT))
                    .isEmpty();
        }
    }

    private int countAccounts(String accountNo) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ACCOUNT WHERE ACCOUNT_NO = ?", Integer.class, accountNo);
    }

    private String outboxStatusOf(String accountNo) {
        var statuses = jdbcTemplate.queryForList(
                "SELECT STATUS FROM OUTBOX_EVENT WHERE AGGREGATE_ID = ? AND EVENT_TYPE = 'AccountOpened'",
                String.class,
                accountNo);
        return statuses.isEmpty() ? null : statuses.getFirst();
    }

    private static String randomCif() {
        return "C%09d".formatted(ThreadLocalRandom.current().nextLong(1, 1_000_000_000L));
    }
}
