package vn.bank.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import lombok.Getter;
import vn.bank.common.error.BusinessException;

@Entity
@Table(name = "ACCOUNT")
@Getter
public class Account {

    /** So tai khoan thanh toan toi da cua mot khach hang. */
    public static final int MAX_CURRENT_ACCOUNTS_PER_CUSTOMER = 3;

    static final String DEFAULT_CURRENCY = "VND";
    private static final int MONEY_SCALE = 2;

    @Id
    @Column(name = "ACCOUNT_NO")
    private String accountNo;

    @Column(name = "CIF")
    private String cif;

    @Enumerated(EnumType.STRING)
    @Column(name = "ACCOUNT_TYPE")
    private AccountType accountType;

    @Column(name = "CURRENCY")
    private String currency;

    @Column(name = "BALANCE")
    private BigDecimal balance;

    @Column(name = "AVAILABLE_BALANCE")
    private BigDecimal availableBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private AccountStatus status;

    // Kieu wrapper: khoa gan san, version null giup Spring Data nhan ra entity moi (persist, khong merge)
    @Version
    @Column(name = "VERSION")
    private Long version;

    @Column(name = "CREATED_AT")
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    protected Account() {}

    private Account(String accountNo, String cif, AccountType accountType, Instant now) {
        var zero = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        this.accountNo = accountNo;
        this.cif = cif;
        this.accountType = accountType;
        this.currency = DEFAULT_CURRENCY;
        this.balance = zero;
        this.availableBalance = zero;
        this.status = AccountStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Mo tai khoan VND moi o trang thai ACTIVE, so du 0. Chu tai khoan phai ACTIVE va dang co it hon
     * {@link #MAX_CURRENT_ACCOUNTS_PER_CUSTOMER} tai khoan thanh toan ({@code existingAccounts}).
     */
    public static Account open(
            String accountNo, AccountHolder holder, AccountType accountType, long existingAccounts, Clock clock) {
        if (!holder.isActive()) {
            throw new BusinessException(
                    AccountErrorCode.CUSTOMER_NOT_ACTIVE,
                    "Customer " + holder.cif() + " is " + holder.customerStatus());
        }
        if (existingAccounts >= MAX_CURRENT_ACCOUNTS_PER_CUSTOMER) {
            throw new BusinessException(
                    AccountErrorCode.ACCOUNT_LIMIT_EXCEEDED,
                    "Customer " + holder.cif() + " already has " + existingAccounts + " accounts, maximum is "
                            + MAX_CURRENT_ACCOUNTS_PER_CUSTOMER);
        }
        return new Account(accountNo, holder.cif(), accountType, clock.instant());
    }

    public String getMaskedAccountNo() {
        return AccountNumberMasker.mask(accountNo);
    }
}
