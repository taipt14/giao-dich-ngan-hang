package vn.bank.account.application;

import java.math.BigDecimal;

/** Yeu cau nap tien mat tai quay; {@code idempotencyKey} la REFERENCE_ID cua journal DEPOSIT (docs/ledger.md 4.1). */
public record DepositCommand(String accountNo, String idempotencyKey, BigDecimal amount, String description) {}
