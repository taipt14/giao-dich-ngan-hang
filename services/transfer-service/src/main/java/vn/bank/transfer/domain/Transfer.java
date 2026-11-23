package vn.bank.transfer.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import vn.bank.common.error.BusinessException;

/**
 * Lenh chuyen khoan. Trang thai chi doi qua {@link #changeStatus} / {@link #fail} theo bang chuyen cua
 * {@link TransferStatus}; moi lan doi (ke ca luc tao lenh) them mot dong {@link TransferStatusHistory}, duoc ghi
 * xuong DB cung transaction voi lenh (cascade).
 */
@Entity
@Table(name = "TRANSFER")
@Getter
public class Transfer {

    private static final int MONEY_SCALE = 2;

    @Id
    @Column(name = "TRANSFER_ID")
    private String transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "TYPE")
    private TransferType type;

    @Column(name = "SOURCE_ACCOUNT")
    private String sourceAccount;

    @Column(name = "DEST_ACCOUNT")
    private String destAccount;

    // NULL voi lenh noi bo (CK_TRANSFER_DEST_BANK_CODE)
    @Column(name = "DEST_BANK_CODE")
    private String destBankCode;

    @Column(name = "AMOUNT")
    private BigDecimal amount;

    @Column(name = "FEE")
    private BigDecimal fee;

    @Column(name = "DESCRIPTION")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private TransferStatus status;

    @Column(name = "FAILURE_CODE")
    private String failureCode;

    @Column(name = "CIF")
    private String cif;

    @Column(name = "IDEMPOTENCY_KEY")
    private String idempotencyKey;

    // Kieu wrapper: khoa gan san, version null giup Spring Data nhan ra entity moi (persist, khong merge)
    @Version
    @Column(name = "VERSION")
    private Long version;

    @Column(name = "CREATED_AT")
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    // Mot chieu: lich su khong can tro nguoc ve lenh. updatable=false de Hibernate ghi FK ngay trong lenh INSERT
    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "TRANSFER_ID", nullable = false, updatable = false)
    @OrderBy
    private List<TransferStatusHistory> statusHistory = new ArrayList<>();

    protected Transfer() {}

    private Transfer(String transferId, NewTransfer request, Instant now) {
        this.transferId = transferId;
        this.type = request.type();
        this.sourceAccount = request.sourceAccount();
        this.destAccount = request.destAccount();
        this.destBankCode = request.destBankCode();
        this.amount = request.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        this.fee = request.fee().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        this.description = request.description();
        this.cif = request.cif();
        this.idempotencyKey = request.idempotencyKey();
        this.status = TransferStatus.INITIATED;
        this.createdAt = now;
        this.updatedAt = now;
        statusHistory.add(TransferStatusHistory.of(null, TransferStatus.INITIATED, null, now));
    }

    /** Tao lenh o trang thai INITIATED. So tien phai duong ({@code INVALID_AMOUNT}), phi khong am. */
    public static Transfer initiate(String transferId, NewTransfer request, Clock clock) {
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(TransferErrorCode.INVALID_AMOUNT, "Amount must be positive");
        }
        if (request.fee().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Fee must not be negative");
        }
        return new Transfer(transferId, request, clock.instant());
    }

    /**
     * Chuyen sang {@code target} (khong phai FAILED, dung {@link #fail}). Chuyen khong co trong bang cua
     * {@link TransferStatus}, hoac trang thai khong ap dung cho loai lenh → {@code INVALID_STATUS_TRANSITION}.
     */
    public void changeStatus(TransferStatus target, Clock clock) {
        if (target == TransferStatus.FAILED) {
            throw new IllegalArgumentException("Use fail(failureCode, clock) to move a transfer to FAILED");
        }
        transitionTo(target, null, clock.instant());
    }

    /** Chuyen sang FAILED kem ma loi (luu vao FAILURE_CODE); quy tac chuyen nhu {@link #changeStatus}. */
    public void fail(String failureCode, Clock clock) {
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("Failure code is required to fail a transfer");
        }
        transitionTo(TransferStatus.FAILED, failureCode, clock.instant());
    }

    public boolean isFinal() {
        return status.isFinal();
    }

    /** Lich su doi trang thai theo thu tu xay ra, chi doc. */
    public List<TransferStatusHistory> getStatusHistory() {
        return Collections.unmodifiableList(statusHistory);
    }

    private void transitionTo(TransferStatus target, String targetFailureCode, Instant now) {
        if (!status.canTransitionTo(target) || !type.supports(target)) {
            throw new BusinessException(
                    TransferErrorCode.INVALID_STATUS_TRANSITION,
                    type + " transfer " + transferId + " cannot change from " + status + " to " + target);
        }
        var previousStatus = status;
        this.status = target;
        if (targetFailureCode != null) {
            this.failureCode = targetFailureCode;
        }
        this.updatedAt = now;
        statusHistory.add(TransferStatusHistory.of(previousStatus, target, targetFailureCode, now));
    }
}
