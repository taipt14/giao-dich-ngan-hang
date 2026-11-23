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

    // NULL voi tai khoan GL (CK_ACCOUNT_CIF)
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
     * Mo tai khoan VND moi o trang thai ACTIVE, so du 0. Chi mo duoc loai tai khoan khach hang: tai khoan GL tao san
     * bang migration → {@code ACCOUNT_TYPE_NOT_ALLOWED}. Chu tai khoan phai ACTIVE va dang co it hon
     * {@link #MAX_CURRENT_ACCOUNTS_PER_CUSTOMER} tai khoan thanh toan ({@code existingAccounts}).
     */
    public static Account open(
            String accountNo, AccountHolder holder, AccountType accountType, long existingAccounts, Clock clock) {
        if (!accountType.isCustomerAccount()) {
            throw new BusinessException(
                    AccountErrorCode.ACCOUNT_TYPE_NOT_ALLOWED,
                    "Account type " + accountType + " cannot be opened for a customer");
        }
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

    /**
     * Doi trang thai theo {@link AccountStatus#canTransitionTo}; chuyen khong hop le (ke ca giu nguyen trang thai) →
     * {@code INVALID_STATUS_TRANSITION}. Dong tai khoan con so du → {@code ACCOUNT_BALANCE_NOT_ZERO}.
     */
    public AccountStatusChange changeStatus(AccountStatus target, String reason, Clock clock) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason is required to change account status");
        }
        if (!status.canTransitionTo(target)) {
            throw new BusinessException(
                    AccountErrorCode.INVALID_STATUS_TRANSITION,
                    "Account " + getMaskedAccountNo() + " cannot change from " + status + " to " + target);
        }
        if (target == AccountStatus.CLOSED && balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(
                    AccountErrorCode.ACCOUNT_BALANCE_NOT_ZERO,
                    "Account " + getMaskedAccountNo() + " still has balance, cannot be closed");
        }
        var previousStatus = status;
        var now = clock.instant();
        this.status = target;
        this.updatedAt = now;
        return new AccountStatusChange(accountNo, cif, previousStatus, target, reason, now);
    }

    /** Ghi mot dong so cai vao tai khoan: DEBIT giam so du, CREDIT tang so du (docs/ledger.md muc 2). */
    public void post(EntryDirection direction, BigDecimal amount, Instant now) {
        switch (direction) {
            case DEBIT -> debit(amount, now);
            case CREDIT -> credit(amount, now);
        }
    }

    /**
     * Tru tien. Tai khoan khach hang phai ACTIVE ({@code ACCOUNT_NOT_ACTIVE}) va du so du ({@code INSUFFICIENT_BALANCE});
     * tai khoan GL duoc phep am.
     */
    public void debit(BigDecimal amount, Instant now) {
        requirePositive(amount);
        if (accountType.isCustomerAccount()) {
            requireActive();
            if (balance.compareTo(amount) < 0) {
                throw new BusinessException(
                        AccountErrorCode.INSUFFICIENT_BALANCE,
                        "Insufficient balance in account " + getMaskedAccountNo());
            }
        }
        changeBalance(amount.negate(), now);
    }

    /** Cong tien. Tai khoan khach hang phai ACTIVE ({@code ACCOUNT_NOT_ACTIVE}). */
    public void credit(BigDecimal amount, Instant now) {
        requirePositive(amount);
        if (accountType.isCustomerAccount()) {
            requireActive();
        }
        changeBalance(amount, now);
    }

    // Chua co phong toa tien (hold) nen so du kha dung luon thay doi cung so du thuc te
    private void changeBalance(BigDecimal delta, Instant now) {
        this.balance = balance.add(delta);
        this.availableBalance = availableBalance.add(delta);
        this.updatedAt = now;
    }

    private void requireActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new BusinessException(
                    AccountErrorCode.ACCOUNT_NOT_ACTIVE, "Account " + getMaskedAccountNo() + " is " + status);
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(AccountErrorCode.INVALID_AMOUNT, "Amount must be positive");
        }
    }

    public AccountBalance toBalance() {
        return new AccountBalance(accountNo, cif, currency, balance, availableBalance);
    }

    /** Tai khoan GL khong co CIF nen khong thuoc khach hang nao. */
    public boolean isOwnedBy(String customerCif) {
        return cif != null && cif.equals(customerCif);
    }

    public String getMaskedAccountNo() {
        return AccountNumberMasker.mask(accountNo);
    }
}
