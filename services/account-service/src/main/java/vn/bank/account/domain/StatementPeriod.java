package vn.bank.account.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import vn.bank.common.error.BusinessException;

/**
 * Khoang ngay nghiep vu cua sao ke ({@link BankZone#ZONE}), tinh ca hai dau. Dong so cai thuoc khoang khi
 * {@code CREATED_AT} trong {@code [from 00:00, (to + 1) 00:00)} gio Viet Nam.
 */
public record StatementPeriod(LocalDate from, LocalDate to) {

    /** So ngay toi da cua mot sao ke, tinh ca hai dau. */
    public static final int MAX_DAYS = 90;

    /** So ngay mac dinh (ke ca hom nay) khi khach hang khong truyen ngay bat dau. */
    public static final int DEFAULT_DAYS = 30;

    /**
     * Thieu {@code to} → {@code today}; thieu {@code from} → {@value #DEFAULT_DAYS} ngay ket thuc o {@code to}.
     * {@code from > to} → {@code STATEMENT_RANGE_INVALID}; dai hon {@value #MAX_DAYS} ngay →
     * {@code STATEMENT_RANGE_TOO_LARGE}. {@code to} o tuong lai duoc chap nhan.
     */
    public static StatementPeriod resolve(LocalDate from, LocalDate to, LocalDate today) {
        var end = to != null ? to : today;
        var start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BusinessException(
                    AccountErrorCode.STATEMENT_RANGE_INVALID, "Statement from " + start + " is after to " + end);
        }
        var days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_DAYS) {
            throw new BusinessException(
                    AccountErrorCode.STATEMENT_RANGE_TOO_LARGE,
                    "Statement range is " + days + " days, maximum is " + MAX_DAYS);
        }
        return new StatementPeriod(start, end);
    }

    /** Thoi diem bat dau (bao gom): 00:00 gio Viet Nam ngay {@code from}. */
    public Instant startInclusive() {
        return from.atStartOfDay(BankZone.ZONE).toInstant();
    }

    /** Thoi diem ket thuc (khong bao gom): 00:00 gio Viet Nam ngay sau {@code to}. */
    public Instant endExclusive() {
        return to.plusDays(1).atStartOfDay(BankZone.ZONE).toInstant();
    }
}
