package vn.bank.transfer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;

/**
 * Mot dong lich su doi trang thai lenh chuyen khoan. Chi them moi, khong sua; chi {@link Transfer} tao ra (cot
 * TRANSFER_ID do quan he trong {@link Transfer} ghi).
 */
@Entity
@Table(name = "TRANSFER_STATUS_HISTORY")
@Getter
public class TransferStatusHistory {

    private static final String ID_GENERATOR = "transferStatusHistoryId";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = ID_GENERATOR)
    // allocationSize phai bang INCREMENT BY cua sequence (ddl-auto=validate kiem tra)
    @SequenceGenerator(name = ID_GENERATOR, sequenceName = "SEQ_TRANSFER_STATUS_HISTORY", allocationSize = 1)
    @Column(name = "TRANSFER_STATUS_HISTORY_ID")
    private Long id;

    // NULL o dong dau tien (lenh vua tao)
    @Enumerated(EnumType.STRING)
    @Column(name = "PREVIOUS_STATUS")
    private TransferStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private TransferStatus status;

    @Column(name = "FAILURE_CODE")
    private String failureCode;

    @Column(name = "CHANGED_AT")
    private Instant changedAt;

    @Version
    @Column(name = "VERSION")
    private Long version;

    protected TransferStatusHistory() {}

    private TransferStatusHistory(
            TransferStatus previousStatus, TransferStatus status, String failureCode, Instant changedAt) {
        this.previousStatus = previousStatus;
        this.status = status;
        this.failureCode = failureCode;
        this.changedAt = changedAt;
    }

    static TransferStatusHistory of(
            TransferStatus previousStatus, TransferStatus status, String failureCode, Instant changedAt) {
        return new TransferStatusHistory(previousStatus, status, failureCode, changedAt);
    }
}
