package vn.bank.account.application;

import java.time.LocalDate;

/**
 * Yeu cau sao ke cua khach hang {@code cif}. {@code from}, {@code to}, {@code cursor} co the null: khoang ngay mac dinh
 * theo {@link vn.bank.account.domain.StatementPeriod#resolve}, khong co cursor la trang dau.
 */
public record StatementQuery(String cif, String accountNo, LocalDate from, LocalDate to, Long cursor, int size) {}
