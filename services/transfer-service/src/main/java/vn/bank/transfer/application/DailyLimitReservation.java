package vn.bank.transfer.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Phan han muc ngay da giu cho mot lenh: {@code amount} tu tai khoan {@code sourceAccount} trong ngay {@code day}. */
public record DailyLimitReservation(String sourceAccount, LocalDate day, BigDecimal amount) {}
