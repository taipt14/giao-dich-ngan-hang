package vn.bank.account.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Mot dong sao ke. {@code amount} co dau de hien thi (CREDIT duong, DEBIT am), {@code direction} giu huong goc; so cai
 * van luu so tien duong (muc 4.1 convention).
 */
public record StatementLine(
        Long entryId,
        Instant createdAt,
        ReferenceType referenceType,
        String description,
        EntryDirection direction,
        BigDecimal amount,
        BigDecimal balanceAfter) {

    private static final int MONEY_SCALE = 2;

    /**
     * Ghep dong so cai voi journal chua no: loai giao dich va mo ta lay tu journal. So tien dua ve scale 2 vi Oracle
     * NUMBER khong giu so 0 cuoi (1000000.00 doc ra la 1000000).
     */
    public static StatementLine of(LedgerEntry entry, Journal journal) {
        if (!journal.getId().equals(entry.getJournalId())) {
            throw new IllegalArgumentException(
                    "Journal " + journal.getId() + " does not contain ledger entry " + entry.getId());
        }
        return new StatementLine(
                entry.getId(),
                entry.getCreatedAt(),
                journal.getReferenceType(),
                journal.getDescription(),
                entry.getDirection(),
                toMoney(entry.signedAmount()),
                toMoney(entry.getBalanceAfter()));
    }

    private static BigDecimal toMoney(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
