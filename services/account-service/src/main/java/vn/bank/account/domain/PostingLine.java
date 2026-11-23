package vn.bank.account.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Mot dong No hoac Co cua yeu cau posting, chua ghi so. Quy tac so tien kiem tra trong {@link Journal#create}. */
public record PostingLine(String accountNo, EntryDirection direction, BigDecimal amount) {

    static final int MONEY_SCALE = 2;

    public PostingLine {
        Objects.requireNonNull(accountNo, "accountNo");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
        // Dua so tien ve scale 2 khi khong phai lam tron (2200 → 2200.00); scale lon hon de Journal#create tu choi
        if (amount.scale() < MONEY_SCALE) {
            amount = amount.setScale(MONEY_SCALE);
        }
    }

    /** So tien co dau theo huong: CREDIT duong, DEBIT am. Chi dung de tinh, khong luu (muc 4.1 convention). */
    public BigDecimal signedAmount() {
        return direction == EntryDirection.CREDIT ? amount : amount.negate();
    }
}
