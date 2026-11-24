package vn.bank.transfer.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.bank.common.test.AbstractIntegrationTest;
import vn.bank.transfer.domain.NewTransfer;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferStatus;

/** Bang TRANSFER, TRANSFER_STATUS_HISTORY (V2) va mapping JPA: moi lan doi trang thai duoc ghi xuong DB. */
@SpringBootTest
class TransferRepositoryIT extends AbstractIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-23T03:00:00Z");
    private static final Instant CHANGED_AT = Instant.parse("2026-11-23T03:01:00Z");
    private static final Clock CREATE_CLOCK = Clock.fixed(CREATED_AT, ZoneOffset.UTC);
    private static final Clock CLOCK = Clock.fixed(CHANGED_AT, ZoneOffset.UTC);
    private static final String INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE";

    private final TransferRepository transferRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    TransferRepositoryIT(
            @Autowired TransferRepository transferRepository,
            @Autowired JdbcTemplate jdbcTemplate,
            @Autowired PlatformTransactionManager transactionManager) {
        this.transferRepository = transferRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void should_persistTransferWithInitialHistory_when_transferInitiated() {
        // given
        var transfer = Transfer.initiate(randomId(), newTransfer(randomId()), CREATE_CLOCK);

        // when
        transactionTemplate.executeWithoutResult(status -> transferRepository.save(transfer));

        // then
        var row = jdbcTemplate.queryForMap(
                "SELECT TYPE, STATUS, AMOUNT, FEE, VERSION FROM TRANSFER WHERE TRANSFER_ID = ?",
                transfer.getTransferId());
        assertThat(row.get("TYPE")).isEqualTo("INTERNAL");
        assertThat(row.get("STATUS")).isEqualTo("INITIATED");
        assertThat((BigDecimal) row.get("AMOUNT")).isEqualByComparingTo("300000");
        assertThat((BigDecimal) row.get("FEE")).isEqualByComparingTo("0");
        assertThat(historyOf(transfer.getTransferId()))
                .containsExactly(tuple(null, "INITIATED", null, utc(CREATED_AT)));
    }

    @Test
    void should_recordEveryStatusChangeInHistory_when_statusChangedInSeparateTransactions() {
        // given
        var transferId = randomId();
        transactionTemplate.executeWithoutResult(status ->
                transferRepository.save(Transfer.initiate(transferId, newTransfer(randomId()), CREATE_CLOCK)));

        // when: moi buoc mot transaction, giong luong that (tao lenh, gui OTP, xac thuc, posting)
        changeStatus(transferId, TransferStatus.OTP_PENDING);
        changeStatus(transferId, TransferStatus.PROCESSING);
        transactionTemplate.executeWithoutResult(
                status -> transferRepository.findById(transferId).orElseThrow().fail(INSUFFICIENT_BALANCE, CLOCK));

        // then
        var row = jdbcTemplate.queryForMap(
                "SELECT STATUS, FAILURE_CODE, VERSION FROM TRANSFER WHERE TRANSFER_ID = ?", transferId);
        assertThat(row.get("STATUS")).isEqualTo("FAILED");
        assertThat(row.get("FAILURE_CODE")).isEqualTo(INSUFFICIENT_BALANCE);
        assertThat(((BigDecimal) row.get("VERSION")).longValue()).isEqualTo(3L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT UPDATED_AT FROM TRANSFER WHERE TRANSFER_ID = ?", LocalDateTime.class, transferId))
                .isEqualTo(utc(CHANGED_AT));
        assertThat(historyOf(transferId))
                .containsExactly(
                        tuple(null, "INITIATED", null, utc(CREATED_AT)),
                        tuple("INITIATED", "OTP_PENDING", null, utc(CHANGED_AT)),
                        tuple("OTP_PENDING", "PROCESSING", null, utc(CHANGED_AT)),
                        tuple("PROCESSING", "FAILED", INSUFFICIENT_BALANCE, utc(CHANGED_AT)));
    }

    @Test
    void should_loadHistoryInOrder_when_transferReadBack() {
        // given
        var transferId = randomId();
        transactionTemplate.executeWithoutResult(status ->
                transferRepository.save(Transfer.initiate(transferId, newTransfer(randomId()), CREATE_CLOCK)));
        changeStatus(transferId, TransferStatus.OTP_PENDING);

        // when
        var statuses = transactionTemplate.execute(
                status -> transferRepository.findById(transferId).orElseThrow().getStatusHistory().stream()
                        .map(history -> history.getStatus())
                        .toList());

        // then
        assertThat(statuses).containsExactly(TransferStatus.INITIATED, TransferStatus.OTP_PENDING);
    }

    @Test
    void should_rejectSecondTransfer_when_sameCifAndIdempotencyKey() {
        // given
        var idempotencyKey = randomId();
        transactionTemplate.executeWithoutResult(status ->
                transferRepository.save(Transfer.initiate(randomId(), newTransfer(idempotencyKey), CREATE_CLOCK)));
        var duplicate = Transfer.initiate(randomId(), newTransfer(idempotencyKey), CREATE_CLOCK);

        // when
        var thrown = catchThrowable(
                () -> transactionTemplate.executeWithoutResult(status -> transferRepository.saveAndFlush(duplicate)));

        // then
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void changeStatus(String transferId, TransferStatus target) {
        transactionTemplate.executeWithoutResult(
                status -> transferRepository.findById(transferId).orElseThrow().changeStatus(target, CLOCK));
    }

    private List<Tuple> historyOf(String transferId) {
        return jdbcTemplate.query(
                "SELECT PREVIOUS_STATUS, STATUS, FAILURE_CODE, CHANGED_AT FROM TRANSFER_STATUS_HISTORY"
                        + " WHERE TRANSFER_ID = ? ORDER BY TRANSFER_STATUS_HISTORY_ID",
                (rs, rowNum) -> tuple(
                        rs.getString("PREVIOUS_STATUS"),
                        rs.getString("STATUS"),
                        rs.getString("FAILURE_CODE"),
                        rs.getObject("CHANGED_AT", LocalDateTime.class)),
                transferId);
    }

    private static NewTransfer newTransfer(String idempotencyKey) {
        return new NewTransfer(
                "C000000001",
                idempotencyKey,
                "100000000001",
                "100000000002",
                null,
                new BigDecimal("300000"),
                BigDecimal.ZERO,
                "Chuyen tien");
    }

    // Cot TIMESTAMP luu gio UTC (hibernate.jdbc.time_zone=UTC), doc ra khong kem mui gio
    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String randomId() {
        return UUID.randomUUID().toString();
    }
}
