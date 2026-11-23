package vn.bank.transfer.application;

import vn.bank.transfer.domain.Transfer;

/** {@code created} false: request lap lai cung Idempotency-Key, {@code transfer} la lenh da tao truoc do. */
public record CreateTransferResult(Transfer transfer, boolean created) {}
