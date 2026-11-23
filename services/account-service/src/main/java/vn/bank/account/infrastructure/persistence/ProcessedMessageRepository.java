package vn.bank.account.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Bang {@code PROCESSED_MESSAGE} cho consumer Kafka idempotent. Dung JdbcTemplate de tham gia transaction JPA dang mo
 * ma loi trung khoa khong danh dau transaction rollback-only (khac voi method repository Spring Data).
 */
@Repository
@RequiredArgsConstructor
public class ProcessedMessageRepository {

    private static final String INSERT_SQL =
            "INSERT INTO PROCESSED_MESSAGE (EVENT_ID, CONSUMER, PROCESSED_AT) VALUES (?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Ghi nhan message da xu ly trong transaction hien tai. INSERT truc tiep thay vi kiem tra roi ghi: hai lan nhan dong
     * thoi cung event thi lan sau cho lan truoc commit roi gap trung khoa chinh.
     *
     * @return {@code false} neu {@code (eventId, consumer)} da duoc ghi truoc do (message trung)
     */
    public boolean markProcessed(UUID eventId, String consumer, Instant processedAt) {
        try {
            // Oracle chi rollback cau lenh loi, transaction van dung tiep duoc
            jdbcTemplate.update(INSERT_SQL, eventId.toString(), consumer, Timestamp.from(processedAt));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
