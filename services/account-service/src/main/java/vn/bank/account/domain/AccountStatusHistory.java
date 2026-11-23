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
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;

/** Mot dong lich su doi trang thai tai khoan. Chi them moi, khong sua. */
@Entity
@Table(name = "ACCOUNT_STATUS_HISTORY")
@Getter
public class AccountStatusHistory {

    private static final String ID_GENERATOR = "accountStatusHistoryId";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = ID_GENERATOR)
    // allocationSize phai bang INCREMENT BY cua sequence (ddl-auto=validate kiem tra)
    @SequenceGenerator(name = ID_GENERATOR, sequenceName = "SEQ_ACCOUNT_STATUS_HISTORY", allocationSize = 1)
    @Column(name = "ACCOUNT_STATUS_HISTORY_ID")
    private Long id;

    @Column(name = "ACCOUNT_NO")
    private String accountNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "PREVIOUS_STATUS")
    private AccountStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private AccountStatus status;

    @Column(name = "REASON")
    private String reason;

    @Column(name = "CHANGED_AT")
    private Instant changedAt;

    @Version
    @Column(name = "VERSION")
    private Long version;

    protected AccountStatusHistory() {}

    private AccountStatusHistory(AccountStatusChange change) {
        this.accountNo = change.accountNo();
        this.previousStatus = change.previousStatus();
        this.status = change.status();
        this.reason = change.reason();
        this.changedAt = change.changedAt();
    }

    public static AccountStatusHistory of(AccountStatusChange change) {
        return new AccountStatusHistory(change);
    }
}
