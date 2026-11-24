package vn.bank.transfer.domain;

import java.time.Instant;
import java.time.LocalDate;
import vn.bank.common.error.BusinessException;

/**
 * Khoang ngay nghiep vu ({@link BankZone#ZONE}) loc lich su lenh chuyen khoan, tinh ca hai dau: lenh thuoc khoang khi
 * {@code CREATED_AT} trong {@code [from 00:00, (to + 1) 00:00)} gio Viet Nam. Moi dau co the null (khong gioi han).
 */
public record TransferPeriod(LocalDate from, LocalDate to) {

    /** {@code from > to} → {@code TRANSFER_RANGE_INVALID}. */
    public static TransferPeriod of(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(
                    TransferErrorCode.TRANSFER_RANGE_INVALID, "Transfer period from " + from + " is after to " + to);
        }
        return new TransferPeriod(from, to);
    }

    /** Thoi diem bat dau (bao gom): 00:00 gio Viet Nam ngay {@code from}; null khi khong co {@code from}. */
    public Instant startInclusive() {
        return from == null ? null : from.atStartOfDay(BankZone.ZONE).toInstant();
    }

    /** Thoi diem ket thuc (khong bao gom): 00:00 gio Viet Nam ngay sau {@code to}; null khi khong co {@code to}. */
    public Instant endExclusive() {
        return to == null ? null : to.plusDays(1).atStartOfDay(BankZone.ZONE).toInstant();
    }
}
