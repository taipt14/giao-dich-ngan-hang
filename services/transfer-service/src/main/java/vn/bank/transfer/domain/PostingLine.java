package vn.bank.transfer.domain;

import java.math.BigDecimal;

/** Mot dong No/Co cua journal can ghi o account-service; so tien luon duong, huong tien nam o {@code direction}. */
public record PostingLine(String accountNo, PostingDirection direction, BigDecimal amount) {}
