package vn.bank.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/**
 * Mot loi doi soat cua mot ngay nghiep vu. Chi {@code PKG_RECONCILE.RUN} ghi bang nay nen entity chi doc
 * ({@code @Immutable}, khong co factory hay {@code @Version}). Y nghia {@code expectedValue}/{@code actualValue} theo
 * {@link ReconcileErrorType}: BALANCE_MISMATCH la tong so cai / ACCOUNT.BALANCE, JOURNAL_UNBALANCED la tong No / tong
 * Co.
 */
@Entity
@Table(name = "RECONCILE_RESULT")
@Immutable
@Getter
public class ReconcileResult {

    private static final int MONEY_SCALE = 2;

    @Id
    @Column(name = "RECONCILE_RESULT_ID")
    private Long id;

    @Column(name = "BUSINESS_DATE")
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "ERROR_TYPE")
    private ReconcileErrorType errorType;

    @Column(name = "ACCOUNT_NO")
    private String accountNo;

    @Column(name = "JOURNAL_ID")
    private Long journalId;

    @Column(name = "EXPECTED_VALUE")
    private BigDecimal expectedValue;

    @Column(name = "ACTUAL_VALUE")
    private BigDecimal actualValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private ReconcileStatus status;

    @Column(name = "RUN_ID")
    private Long runId;

    @Column(name = "DETECTED_AT")
    private Instant detectedAt;

    @Column(name = "CHECKED_AT")
    private Instant checkedAt;

    protected ReconcileResult() {}

    // Oracle tra NUMBER(19,2) theo scale tu nhien (100 thay vi 100.00): dua ve scale 2 nhu moi so tien khac, khong lam
    // tron
    public BigDecimal getExpectedValue() {
        return expectedValue.setScale(MONEY_SCALE);
    }

    public BigDecimal getActualValue() {
        return actualValue.setScale(MONEY_SCALE);
    }

    /** Doi tuong cua loi de ghi log: so tai khoan da che hoac journal id. */
    public String describeTarget() {
        return errorType == ReconcileErrorType.BALANCE_MISMATCH
                ? "account " + AccountNumberMasker.mask(accountNo)
                : "journal " + journalId;
    }
}
