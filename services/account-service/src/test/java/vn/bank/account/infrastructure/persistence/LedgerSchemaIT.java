package vn.bank.account.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.bank.common.test.AbstractIntegrationTest;

/**
 * Kiem tra schema so cai (V4, V5) truc tiep bang SQL. Du lieu ghi vao LEDGER_ENTRY khong xoa duoc (trigger chan), nen
 * moi test ghi du lieu deu chay trong transaction roi rollback.
 */
@SpringBootTest
class LedgerSchemaIT extends AbstractIntegrationTest {

    private static final String MIGRATION_LOCATION = "classpath:db/migration";
    private static final String ORACLE_ADMIN_USER = "system";
    private static final String CASH_VAULT = "CASH_VAULT";
    private static final String LEDGER_IMMUTABLE_ERROR = "ORA-20001";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    LedgerSchemaIT(@Autowired JdbcTemplate jdbcTemplate, @Autowired PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void should_migrateSuccessfully_when_runOnEmptySchema() {
        // given: schema moi tinh, cung quyen voi ACCOUNT_SVC trong infra/oracle/init
        var admin = new JdbcTemplate(
                new DriverManagerDataSource(ORACLE.getJdbcUrl(), ORACLE_ADMIN_USER, SERVICE_DB_PASSWORD));
        var schema = "LEDGER_IT_" + randomSuffix();
        var password = "P" + randomSuffix();
        admin.execute("CREATE USER " + schema + " IDENTIFIED BY \"" + password + "\""
                + " DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS");
        admin.execute("GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW,"
                + " CREATE PROCEDURE, CREATE TRIGGER, CREATE SYNONYM TO " + schema);
        try {
            // when
            var result = Flyway.configure()
                    .dataSource(ORACLE.getJdbcUrl(), schema, password)
                    .locations(MIGRATION_LOCATION)
                    .load()
                    .migrate();

            // then
            assertThat(result.success).isTrue();
            assertThat(admin.queryForList("SELECT TABLE_NAME FROM ALL_TABLES WHERE OWNER = ?", String.class, schema))
                    .contains("ACCOUNT", "JOURNAL", "LEDGER_ENTRY");
            assertThat(admin.queryForObject(
                            "SELECT STATUS FROM ALL_TRIGGERS WHERE OWNER = ? AND TRIGGER_NAME = ?",
                            String.class,
                            schema,
                            "TRG_LEDGER_ENTRY_IMMUTABLE"))
                    .isEqualTo("ENABLED");
            assertThat(admin.queryForObject(
                            "SELECT COUNT(*) FROM " + schema + ".ACCOUNT WHERE ACCOUNT_TYPE = 'GL'", Integer.class))
                    .isEqualTo(3);
        } finally {
            admin.execute("DROP USER " + schema + " CASCADE");
        }
    }

    @Test
    void should_seedActiveGlAccountsWithoutCif_when_migrationApplied() {
        // given: Flyway da chay khi context khoi dong

        // when
        var glAccounts = jdbcTemplate.queryForList(
                "SELECT ACCOUNT_NO FROM ACCOUNT WHERE ACCOUNT_TYPE = 'GL' AND CIF IS NULL"
                        + " AND STATUS = 'ACTIVE' AND CURRENCY = 'VND'",
                String.class);

        // then
        assertThat(glAccounts).containsExactlyInAnyOrder(CASH_VAULT, "FEE_INCOME", "NAPAS_SETTLEMENT");
    }

    @Test
    void should_rejectUpdate_when_updatingLedgerEntry() {
        // given / when
        var outcome = inRollbackTransaction(() -> {
            var entryId = givenLedgerEntry("100.00");
            var thrown = catchThrowable(
                    () -> jdbcTemplate.update("UPDATE LEDGER_ENTRY SET AMOUNT = 1 WHERE ENTRY_ID = ?", entryId));
            return new Outcome(thrown, amountOf(entryId));
        });

        // then
        assertThat(outcome.thrown())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining(LEDGER_IMMUTABLE_ERROR);
        assertThat(outcome.amountAfter()).isEqualTo("100");
    }

    @Test
    void should_rejectDelete_when_deletingLedgerEntry() {
        // given / when
        var outcome = inRollbackTransaction(() -> {
            var entryId = givenLedgerEntry("100.00");
            var thrown =
                    catchThrowable(() -> jdbcTemplate.update("DELETE FROM LEDGER_ENTRY WHERE ENTRY_ID = ?", entryId));
            return new Outcome(thrown, amountOf(entryId));
        });

        // then
        assertThat(outcome.thrown())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining(LEDGER_IMMUTABLE_ERROR);
        assertThat(outcome.amountAfter()).isEqualTo("100");
    }

    @Test
    void should_rejectEntry_when_amountNotPositive() {
        // given / when
        var thrown = inRollbackTransaction(() -> catchThrowable(() -> givenLedgerEntry("0.00")));

        // then
        assertThat(thrown)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_LEDGER_ENTRY_AMOUNT");
    }

    @Test
    void should_rejectDuplicateJournal_when_sameReferenceTypeAndReferenceId() {
        // given / when
        var referenceId = UUID.randomUUID().toString();
        var thrown = inRollbackTransaction(() -> {
            givenJournal("TRANSFER", referenceId);
            return catchThrowable(() -> givenJournal("TRANSFER", referenceId));
        });

        // then
        assertThat(thrown)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("UK_JOURNAL_REFERENCE_ID_TYPE");
    }

    @Test
    void should_allowJournalWithSameReferenceId_when_referenceTypeDiffers() {
        // given: lenh chuyen khoan bi dao (BANK-053) dung lai transferId voi REVERSAL
        var referenceId = UUID.randomUUID().toString();

        // when
        var journals = inRollbackTransaction(() -> {
            givenJournal("TRANSFER", referenceId);
            givenJournal("REVERSAL", referenceId);
            return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM JOURNAL WHERE REFERENCE_ID = ?", Integer.class, referenceId);
        });

        // then
        assertThat(journals).isEqualTo(2);
    }

    @Test
    void should_allowNegativeBalance_when_accountIsGl() {
        // given / when
        var balance = inRollbackTransaction(() -> {
            jdbcTemplate.update("UPDATE ACCOUNT SET BALANCE = -1000000 WHERE ACCOUNT_NO = ?", CASH_VAULT);
            return jdbcTemplate.queryForObject(
                    "SELECT TO_CHAR(BALANCE) FROM ACCOUNT WHERE ACCOUNT_NO = ?", String.class, CASH_VAULT);
        });

        // then
        assertThat(balance).isEqualTo("-1000000");
    }

    @Test
    void should_rejectGlAccount_when_cifPresent() {
        // given / when
        var thrown = inRollbackTransaction(() -> catchThrowable(() -> insertAccount("GL", "C000000001")));

        // then
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("CK_ACCOUNT_CIF");
    }

    @Test
    void should_rejectCustomerAccount_when_cifMissing() {
        // given / when
        var thrown = inRollbackTransaction(() -> catchThrowable(() -> insertAccount("CURRENT", null)));

        // then
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("CK_ACCOUNT_CIF");
    }

    @Test
    void should_indexLedgerEntryByAccountNoAndCreatedAt_when_migrationApplied() {
        // given: index phuc vu sao ke (BANK-033)

        // when
        var columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM USER_IND_COLUMNS WHERE INDEX_NAME = ? ORDER BY COLUMN_POSITION",
                String.class,
                "IX_LEDGER_ENTRY_ACC_CREATED");

        // then
        assertThat(columns).containsExactly("ACCOUNT_NO", "CREATED_AT");
    }

    private record Outcome(Throwable thrown, String amountAfter) {}

    private <T> T inRollbackTransaction(Supplier<T> action) {
        return transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return action.get();
        });
    }

    private long givenLedgerEntry(String amount) {
        var journalId = givenJournal("DEPOSIT", UUID.randomUUID().toString());
        var entryId = jdbcTemplate.queryForObject("SELECT SEQ_LEDGER_ENTRY.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                "INSERT INTO LEDGER_ENTRY (ENTRY_ID, JOURNAL_ID, ACCOUNT_NO, DIRECTION, AMOUNT, BALANCE_AFTER, CREATED_AT)"
                        + " VALUES (?, ?, ?, 'DEBIT', ?, ?, SYS_EXTRACT_UTC(SYSTIMESTAMP))",
                entryId,
                journalId,
                CASH_VAULT,
                new BigDecimal(amount),
                new BigDecimal(amount).negate());
        return entryId;
    }

    private long givenJournal(String referenceType, String referenceId) {
        var journalId = jdbcTemplate.queryForObject("SELECT SEQ_JOURNAL.NEXTVAL FROM DUAL", Long.class);
        jdbcTemplate.update(
                "INSERT INTO JOURNAL (JOURNAL_ID, REFERENCE_TYPE, REFERENCE_ID, DESCRIPTION, CREATED_AT)"
                        + " VALUES (?, ?, ?, 'Ledger schema IT', SYS_EXTRACT_UTC(SYSTIMESTAMP))",
                journalId,
                referenceType,
                referenceId);
        return journalId;
    }

    private String amountOf(long entryId) {
        return jdbcTemplate.queryForObject(
                "SELECT TO_CHAR(AMOUNT) FROM LEDGER_ENTRY WHERE ENTRY_ID = ?", String.class, entryId);
    }

    private void insertAccount(String accountType, String cif) {
        jdbcTemplate.update(
                "INSERT INTO ACCOUNT (ACCOUNT_NO, CIF, ACCOUNT_TYPE, CURRENCY, BALANCE, AVAILABLE_BALANCE, STATUS,"
                        + " VERSION, CREATED_AT, UPDATED_AT) VALUES (?, ?, ?, 'VND', 0, 0, 'ACTIVE', 0,"
                        + " SYS_EXTRACT_UTC(SYSTIMESTAMP), SYS_EXTRACT_UTC(SYSTIMESTAMP))",
                "IT_" + randomSuffix(),
                cif,
                accountType);
    }

    private static String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
