package vn.bank.common.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

/**
 * Truy cap bang {@code OUTBOX_EVENT} bang JDBC. Dung {@link JdbcTemplate} tren cung DataSource voi JPA cua service nen
 * tham gia transaction dang mo (JpaTransactionManager chia se connection), khong can entity JPA (service khong phai
 * quet them package).
 *
 * <p>Thoi diem luu dang {@link LocalDateTime} gia tri UTC, giong cach Hibernate cua service luu {@code Instant}
 * ({@code hibernate.jdbc.time_zone=UTC}).
 */
class OutboxRepository {

    private static final String INSERT = """
            INSERT INTO OUTBOX_EVENT
                (EVENT_ID, AGGREGATE_TYPE, AGGREGATE_ID, TOPIC, EVENT_TYPE, PAYLOAD, STATUS, RETRY_COUNT, CREATED_AT)
            VALUES (?, ?, ?, ?, ?, ?, 'NEW', 0, ?)""";

    /*
     * Oracle khong cho FETCH FIRST cung FOR UPDATE trong mot cau (ORA-02014): subquery chon toi da N dong NEW cu nhat,
     * cau ngoai khoa chung, bo qua dong instance khac dang khoa (SKIP LOCKED). Kiem tra lai STATUS o cau ngoai: dong
     * vua duoc instance khac gui xong (SENT) khong bi lay lai.
     */
    private static final String LOCK_NEXT_BATCH = """
            SELECT EVENT_ID, AGGREGATE_TYPE, AGGREGATE_ID, TOPIC, EVENT_TYPE, PAYLOAD, RETRY_COUNT, CREATED_AT
            FROM OUTBOX_EVENT
            WHERE STATUS = 'NEW'
              AND EVENT_ID IN (
                  SELECT EVENT_ID FROM OUTBOX_EVENT WHERE STATUS = 'NEW' ORDER BY CREATED_AT FETCH FIRST ? ROWS ONLY)
            FOR UPDATE SKIP LOCKED""";

    private static final String MARK_SENT = "UPDATE OUTBOX_EVENT SET STATUS = 'SENT', SENT_AT = ? WHERE EVENT_ID = ?";

    private static final String MARK_ATTEMPT_FAILED =
            "UPDATE OUTBOX_EVENT SET STATUS = ?, RETRY_COUNT = ? WHERE EVENT_ID = ?";

    private static final String DELETE_SENT_BEFORE = "DELETE FROM OUTBOX_EVENT WHERE STATUS = 'SENT' AND SENT_AT < ?";

    private final JdbcTemplate jdbcTemplate;

    OutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insert(OutboxEvent event) {
        jdbcTemplate.update(
                INSERT,
                event.eventId().toString(),
                event.aggregateType(),
                event.aggregateId(),
                event.topic(),
                event.eventType(),
                new SqlParameterValue(Types.CLOB, event.payload()),
                toUtc(event.createdAt()));
    }

    /** Khoa toi da {@code batchSize} event NEW cu nhat ma chua instance nao khoa, sap xep theo thoi diem tao. */
    List<OutboxEvent> lockNextBatch(int batchSize) {
        var events = jdbcTemplate.query(LOCK_NEXT_BATCH, OutboxRepository::toEvent, batchSize);
        return events.stream()
                .sorted((left, right) -> left.createdAt().compareTo(right.createdAt()))
                .toList();
    }

    void markSent(UUID eventId, Instant sentAt) {
        jdbcTemplate.update(MARK_SENT, toUtc(sentAt), eventId.toString());
    }

    /** Ghi so lan gui loi {@code retryCount}; {@code status} la NEW (con thu lai) hoac FAILED (het luot). */
    void markAttemptFailed(UUID eventId, int retryCount, OutboxStatus status) {
        jdbcTemplate.update(MARK_ATTEMPT_FAILED, status.name(), retryCount, eventId.toString());
    }

    /** Xoa event SENT co SENT_AT truoc {@code cutoff}; tra so dong da xoa. */
    int deleteSentBefore(Instant cutoff) {
        return jdbcTemplate.update(DELETE_SENT_BEFORE, toUtc(cutoff));
    }

    private static OutboxEvent toEvent(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxEvent(
                UUID.fromString(rs.getString("EVENT_ID")),
                rs.getString("AGGREGATE_TYPE"),
                rs.getString("AGGREGATE_ID"),
                rs.getString("TOPIC"),
                rs.getString("EVENT_TYPE"),
                rs.getString("PAYLOAD"),
                rs.getInt("RETRY_COUNT"),
                rs.getObject("CREATED_AT", LocalDateTime.class).toInstant(ZoneOffset.UTC));
    }

    private static LocalDateTime toUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
