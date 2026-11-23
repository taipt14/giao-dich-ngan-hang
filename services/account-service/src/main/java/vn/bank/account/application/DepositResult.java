package vn.bank.account.application;

import vn.bank.account.domain.Journal;
import vn.bank.account.domain.LedgerEntry;

/**
 * Journal nap tien va dong Co vao tai khoan khach hang. {@code created = false}: Idempotency-Key da duoc nap tu truoc,
 * day la ket qua cua lan nap do, khong nap them.
 */
public record DepositResult(Journal journal, LedgerEntry creditEntry, boolean created) {}
