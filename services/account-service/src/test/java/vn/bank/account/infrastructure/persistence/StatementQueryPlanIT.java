package vn.bank.account.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.bank.common.test.AbstractIntegrationTest;

/** Execution plan cua truy van sao ke {@link LedgerEntryRepository#STATEMENT_QUERY} tren Oracle that (BANK-033). */
@SpringBootTest
class StatementQueryPlanIT extends AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(StatementQueryPlanIT.class);
    private static final String STATEMENT_INDEX = "IX_LEDGER_ENTRY_ACC_CREATED";

    private final JdbcTemplate jdbcTemplate;

    StatementQueryPlanIT(@Autowired JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void should_rangeScanIndexAccountNoCreatedAt_when_statementQueried() {
        // given: EXPLAIN PLAN chap nhan bind variable chua gan gia tri, plan la plan cua chinh cau SQL repository dung
        var statementId = "BANK033-" + UUID.randomUUID().toString().substring(0, 8);

        // when
        jdbcTemplate.execute(
                "EXPLAIN PLAN SET STATEMENT_ID = '" + statementId + "' FOR " + LedgerEntryRepository.STATEMENT_QUERY);
        var plan = jdbcTemplate.queryForList(
                "SELECT PLAN_TABLE_OUTPUT FROM TABLE(DBMS_XPLAN.DISPLAY('PLAN_TABLE', ?, 'TYPICAL'))",
                String.class,
                statementId);
        var indexScans = jdbcTemplate.queryForList(
                "SELECT OPERATION || ' ' || OPTIONS FROM PLAN_TABLE WHERE STATEMENT_ID = ? AND OBJECT_NAME = ?",
                String.class,
                statementId,
                STATEMENT_INDEX);

        // then
        log.info("Statement query plan:\n{}", String.join("\n", plan));
        assertThat(indexScans).containsExactly("INDEX RANGE SCAN");
        assertThat(String.join("\n", plan)).contains("access(\"E\".\"ACCOUNT_NO\"=");
    }
}
