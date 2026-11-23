package vn.bank.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import vn.bank.common.error.BusinessException;

/**
 * But toan: mot nghiep vu lam thay doi so du, duy nhat theo {@code (referenceType, referenceId)}. Khong co
 * {@code @Version}: journal ghi mot lan, khong bao gio sua (bang JOURNAL khong co cot VERSION).
 */
@Entity
@Table(name = "JOURNAL")
@Immutable
@Getter
public class Journal {

    private static final String ID_GENERATOR = "journalId";
    private static final int MONEY_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = ID_GENERATOR)
    // allocationSize phai bang INCREMENT BY cua sequence (ddl-auto=validate kiem tra)
    @SequenceGenerator(name = ID_GENERATOR, sequenceName = "SEQ_JOURNAL", allocationSize = 1)
    @Column(name = "JOURNAL_ID")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "REFERENCE_TYPE")
    private ReferenceType referenceType;

    @Column(name = "REFERENCE_ID")
    private String referenceId;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "CREATED_AT")
    private Instant createdAt;

    protected Journal() {}

    private Journal(ReferenceType referenceType, String referenceId, String description, Instant now) {
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.description = description;
        this.createdAt = now;
    }

    /**
     * Tao journal cho cac dong {@code lines}. Moi so tien phai {@code > 0} va toi da 2 chu so thap phan →
     * {@code INVALID_AMOUNT}; tong DEBIT phai bang tong CREDIT → {@code JOURNAL_NOT_BALANCED}. Cac dong deu duong nen
     * journal can bang luon co it nhat mot dong DEBIT va mot dong CREDIT.
     */
    public static Journal create(
            ReferenceType referenceType, String referenceId, String description, List<PostingLine> lines, Instant now) {
        requireValidAmounts(lines);
        requireBalanced(lines);
        return new Journal(referenceType, referenceId, description, now);
    }

    private static void requireValidAmounts(List<PostingLine> lines) {
        for (var line : lines) {
            if (line.amount().compareTo(BigDecimal.ZERO) <= 0 || line.amount().scale() > MONEY_SCALE) {
                throw new BusinessException(
                        AccountErrorCode.INVALID_AMOUNT,
                        "Posting amount must be positive with at most " + MONEY_SCALE + " decimal places");
            }
        }
    }

    private static void requireBalanced(List<PostingLine> lines) {
        var totalDebit = sum(lines, EntryDirection.DEBIT);
        var totalCredit = sum(lines, EntryDirection.CREDIT);
        if (lines.isEmpty() || totalDebit.compareTo(totalCredit) != 0) {
            throw new BusinessException(
                    AccountErrorCode.JOURNAL_NOT_BALANCED,
                    "Total debit " + totalDebit.toPlainString() + " does not equal total credit "
                            + totalCredit.toPlainString());
        }
    }

    private static BigDecimal sum(List<PostingLine> lines, EntryDirection direction) {
        var total = BigDecimal.ZERO;
        for (var line : lines) {
            if (line.direction() == direction) {
                total = total.add(line.amount());
            }
        }
        return total;
    }
}
