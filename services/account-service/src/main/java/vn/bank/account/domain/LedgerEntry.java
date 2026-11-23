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
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/**
 * Dong so cai: mot ve No hoac Co cua mot tai khoan trong journal. Chi ghi them: trigger
 * {@code TRG_LEDGER_ENTRY_IMMUTABLE} chan UPDATE/DELETE, {@code @Immutable} de Hibernate khong bao gio sinh UPDATE.
 * Giu {@code journalId} thay vi quan he JPA: khong can nap journal khi doc dong so cai (sao ke).
 */
@Entity
@Table(name = "LEDGER_ENTRY")
@Immutable
@Getter
public class LedgerEntry {

    private static final String ID_GENERATOR = "ledgerEntryId";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = ID_GENERATOR)
    // allocationSize phai bang INCREMENT BY cua sequence (ddl-auto=validate kiem tra)
    @SequenceGenerator(name = ID_GENERATOR, sequenceName = "SEQ_LEDGER_ENTRY", allocationSize = 1)
    @Column(name = "ENTRY_ID")
    private Long id;

    @Column(name = "JOURNAL_ID")
    private Long journalId;

    @Column(name = "ACCOUNT_NO")
    private String accountNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "DIRECTION")
    private EntryDirection direction;

    @Column(name = "AMOUNT")
    private BigDecimal amount;

    @Column(name = "BALANCE_AFTER")
    private BigDecimal balanceAfter;

    @Column(name = "CREATED_AT")
    private Instant createdAt;

    protected LedgerEntry() {}

    private LedgerEntry(Journal journal, PostingLine line, BigDecimal balanceAfter) {
        this.journalId = journal.getId();
        this.accountNo = line.accountNo();
        this.direction = line.direction();
        this.amount = line.amount();
        this.balanceAfter = balanceAfter;
        this.createdAt = journal.getCreatedAt();
    }

    /**
     * Ghi dong {@code line} vao {@code account} (da khoa) va tao dong so cai kem so du ngay sau dong do. Quy tac trang
     * thai, so du do {@link Account#post} kiem tra. {@code journal} phai da co id (da luu).
     */
    public static LedgerEntry post(Journal journal, Account account, PostingLine line) {
        if (journal.getId() == null) {
            throw new IllegalStateException("Journal must be saved before posting entries");
        }
        if (!account.getAccountNo().equals(line.accountNo())) {
            throw new IllegalArgumentException(
                    "Posting line does not belong to account " + account.getMaskedAccountNo());
        }
        account.post(line.direction(), line.amount(), journal.getCreatedAt());
        return new LedgerEntry(journal, line, account.getBalance());
    }

    /** So tien co dau theo huong: CREDIT duong, DEBIT am. Chi de hien thi, cot AMOUNT luon duong. */
    public BigDecimal signedAmount() {
        return direction == EntryDirection.CREDIT ? amount : amount.negate();
    }
}
