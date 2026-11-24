package vn.bank.transfer.application;

import java.time.LocalDate;
import vn.bank.transfer.domain.TransferStatus;

/**
 * Tra cuu lenh cua khach hang {@code cif}. {@code status}, {@code from}, {@code to} co the null (khong loc);
 * {@code from}/{@code to} la ngay nghiep vu gio Viet Nam, tinh ca hai dau. {@code page} tinh tu 0.
 */
public record TransferSearchQuery(
        String cif, TransferStatus status, LocalDate from, LocalDate to, int page, int size) {}
