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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.Getter;

/**
 * Mot khoang so tien [{@code minAmount}, {@code maxAmount}) trong bieu phi cua mot loai giao dich, ap dung tu ngay
 * nghiep vu {@code effectiveFrom} ({@link BankZone}). Cac dong cung loai va cung ngay hieu luc tao thanh mot bieu phi;
 * chon bieu phi nao do {@link FeeCalculator} quyet dinh. Phi da gom VAT.
 */
@Entity
@Table(name = "FEE_SCHEDULE")
@Getter
public class FeeSchedule {

    private static final String ID_GENERATOR = "feeScheduleId";
    // Phi lam tron den don vi dong
    private static final int FEE_SCALE = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = ID_GENERATOR)
    // allocationSize phai bang INCREMENT BY cua sequence (ddl-auto=validate kiem tra)
    @SequenceGenerator(name = ID_GENERATOR, sequenceName = "SEQ_FEE_SCHEDULE", allocationSize = 1)
    @Column(name = "FEE_SCHEDULE_ID")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "TRANSFER_TYPE")
    private TransferType transferType;

    @Column(name = "MIN_AMOUNT")
    private BigDecimal minAmount;

    // NULL: khong gioi han tren
    @Column(name = "MAX_AMOUNT")
    private BigDecimal maxAmount;

    @Column(name = "FEE_RATE")
    private BigDecimal feeRate;

    @Column(name = "MIN_FEE")
    private BigDecimal minFee;

    @Column(name = "MAX_FEE")
    private BigDecimal maxFee;

    @Column(name = "EFFECTIVE_FROM")
    private LocalDate effectiveFrom;

    @Version
    @Column(name = "VERSION")
    private Long version;

    protected FeeSchedule() {}

    private FeeSchedule(Tier tier, LocalDate effectiveFrom) {
        this.transferType = tier.transferType();
        this.minAmount = tier.minAmount();
        this.maxAmount = tier.maxAmount();
        this.feeRate = tier.feeRate();
        this.minFee = tier.minFee();
        this.maxFee = tier.maxFee();
        this.effectiveFrom = effectiveFrom;
    }

    /** Khoang tien va phi phai hop le giong CHECK cua bang FEE_SCHEDULE. */
    public static FeeSchedule of(Tier tier, LocalDate effectiveFrom) {
        tier.validate();
        return new FeeSchedule(tier, effectiveFrom);
    }

    public boolean appliesTo(TransferType type, LocalDate day) {
        return transferType == type && !effectiveFrom.isAfter(day);
    }

    public boolean covers(BigDecimal amount) {
        return amount.compareTo(minAmount) >= 0 && (maxAmount == null || amount.compareTo(maxAmount) < 0);
    }

    /** {@code amount} x ty le, gioi han trong [phi toi thieu, phi toi da], lam tron HALF_UP den don vi dong. */
    public BigDecimal feeFor(BigDecimal amount) {
        var fee = amount.multiply(feeRate).max(minFee).min(maxFee);
        return fee.setScale(FEE_SCALE, RoundingMode.HALF_UP);
    }

    /** Thong so mot khoang phi; {@code maxAmount} null la khong gioi han tren. */
    public record Tier(
            TransferType transferType,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            BigDecimal feeRate,
            BigDecimal minFee,
            BigDecimal maxFee) {

        void validate() {
            var invalidAmount = minAmount.signum() < 0 || (maxAmount != null && maxAmount.compareTo(minAmount) <= 0);
            var invalidRate = feeRate.signum() < 0 || feeRate.compareTo(BigDecimal.ONE) > 0;
            var invalidFee = minFee.signum() < 0 || maxFee.compareTo(minFee) < 0;
            if (invalidAmount || invalidRate || invalidFee) {
                throw new IllegalArgumentException("Invalid fee schedule tier for " + transferType);
            }
        }
    }
}
