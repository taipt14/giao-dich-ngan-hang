package vn.bank.transfer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.Getter;
import vn.bank.common.error.BusinessException;

/**
 * Han muc chuyen khoan cua mot loai khach hang: toi da moi lenh va tong moi ngay nghiep vu ({@link BankZone}). So tien
 * tinh theo AMOUNT cua lenh, khong gom phi.
 */
@Entity
@Table(name = "TRANSFER_LIMIT_CONFIG")
@Getter
public class TransferLimitConfig {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "CUSTOMER_TYPE")
    private CustomerType customerType;

    @Column(name = "PER_TRANSACTION_LIMIT")
    private BigDecimal perTransactionLimit;

    @Column(name = "DAILY_LIMIT")
    private BigDecimal dailyLimit;

    @Version
    @Column(name = "VERSION")
    private Long version;

    protected TransferLimitConfig() {}

    private TransferLimitConfig(CustomerType customerType, BigDecimal perTransactionLimit, BigDecimal dailyLimit) {
        this.customerType = customerType;
        this.perTransactionLimit = perTransactionLimit;
        this.dailyLimit = dailyLimit;
    }

    /** Han muc moi lenh phai duong, han muc ngay khong nho hon han muc moi lenh (giong CHECK cua bang). */
    public static TransferLimitConfig of(
            CustomerType customerType, BigDecimal perTransactionLimit, BigDecimal dailyLimit) {
        if (perTransactionLimit.compareTo(BigDecimal.ZERO) <= 0 || dailyLimit.compareTo(perTransactionLimit) < 0) {
            throw new IllegalArgumentException("Invalid transfer limit for " + customerType);
        }
        return new TransferLimitConfig(customerType, perTransactionLimit, dailyLimit);
    }

    /** {@code amount} lon hon han muc moi lenh → {@code PER_TRANSACTION_LIMIT_EXCEEDED}. */
    public void requireWithinPerTransactionLimit(BigDecimal amount) {
        if (amount.compareTo(perTransactionLimit) > 0) {
            throw new BusinessException(
                    TransferErrorCode.PER_TRANSACTION_LIMIT_EXCEEDED,
                    "Amount exceeds per-transaction limit " + perTransactionLimit.toPlainString());
        }
    }

    /** Da chuyen {@code usedToday} trong ngay, chuyen them {@code amount} vuot han muc ngay → {@code DAILY_LIMIT_EXCEEDED}. */
    public void requireWithinDailyLimit(BigDecimal usedToday, BigDecimal amount) {
        if (usedToday.add(amount).compareTo(dailyLimit) > 0) {
            throw new BusinessException(
                    TransferErrorCode.DAILY_LIMIT_EXCEEDED, "Amount exceeds daily limit " + dailyLimit.toPlainString());
        }
    }
}
