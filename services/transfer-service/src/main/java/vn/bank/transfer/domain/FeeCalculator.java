package vn.bank.transfer.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;

/**
 * Tinh phi chuyen khoan tu bieu phi (BANK-044), khong phu thuoc Spring hay DB. Bieu phi ap dung cho ngay {@code day}
 * la bieu phi cung loai giao dich co ngay hieu luc gan nhat khong sau {@code day}: bieu phi moi co hieu luc trong tuong
 * lai chua anh huong lenh hom nay.
 */
public final class FeeCalculator {

    private FeeCalculator() {}

    /**
     * Phi cua lenh {@code type} so tien {@code amount} tao trong ngay nghiep vu {@code day}, lam tron HALF_UP den don vi
     * dong. Khong co bieu phi hieu luc hoac bieu phi khong phu khoang tien cua {@code amount} → loi cau hinh
     * ({@link IllegalStateException}).
     */
    public static BigDecimal calculate(
            Collection<FeeSchedule> schedules, TransferType type, BigDecimal amount, LocalDate day) {
        var effectiveFrom = schedules.stream()
                .filter(schedule -> schedule.appliesTo(type, day))
                .map(FeeSchedule::getEffectiveFrom)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("No fee schedule of " + type + " effective on " + day));
        return schedules.stream()
                .filter(schedule -> schedule.appliesTo(type, day))
                .filter(schedule -> schedule.getEffectiveFrom().equals(effectiveFrom))
                .filter(schedule -> schedule.covers(amount))
                .findFirst()
                .map(schedule -> schedule.feeFor(amount))
                .orElseThrow(() -> new IllegalStateException("Fee schedule of " + type + " effective from "
                        + effectiveFrom + " does not cover amount " + amount.toPlainString()));
    }
}
