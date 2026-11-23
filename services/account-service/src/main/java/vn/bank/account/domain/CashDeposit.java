package vn.bank.account.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import vn.bank.common.error.BusinessException;

/**
 * Nap tien mat tai quay vao tai khoan khach hang: No {@link #CASH_VAULT} / Co tai khoan khach hang (docs/ledger.md
 * 4.1). So tien phai {@code > 0} ({@code INVALID_AMOUNT}) va khong vuot {@link #MAX_AMOUNT}
 * ({@code DEPOSIT_LIMIT_EXCEEDED}).
 */
public record CashDeposit(String accountNo, BigDecimal amount) {

    /** Tai khoan GL quy tien mat tai quay (migration V4). */
    public static final String CASH_VAULT = "CASH_VAULT";

    /** So tien toi da cua mot lan nap tai quay (VND). */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("500000000.00");

    private static final int EXPECTED_ENTRIES = 2;

    public CashDeposit {
        Objects.requireNonNull(accountNo, "accountNo");
        Objects.requireNonNull(amount, "amount");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(AccountErrorCode.INVALID_AMOUNT, "Deposit amount must be positive");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(
                    AccountErrorCode.DEPOSIT_LIMIT_EXCEEDED,
                    "Deposit amount must not exceed " + MAX_AMOUNT.toPlainString());
        }
    }

    /**
     * Chi nap vao tai khoan khach hang: nap vao tai khoan GL (ke ca CASH_VAULT) se chuyen tien giua cac tai khoan noi bo
     * → {@code ACCOUNT_TYPE_NOT_ALLOWED}. Trang thai tai khoan kiem tra luc ghi so ({@link Account#credit}).
     */
    public void requireCustomerAccount(Account target) {
        if (!target.getAccountType().isCustomerAccount()) {
            throw new BusinessException(
                    AccountErrorCode.ACCOUNT_TYPE_NOT_ALLOWED,
                    "Cash deposit is not allowed into " + target.getAccountType() + " account " + accountNo);
        }
    }

    public List<PostingLine> toPostingLines() {
        return List.of(
                new PostingLine(CASH_VAULT, EntryDirection.DEBIT, amount),
                new PostingLine(accountNo, EntryDirection.CREDIT, amount));
    }

    /**
     * Journal da co ({@code entries}) co phai chinh lan nap nay khong: dung hai dong No CASH_VAULT / Co tai khoan voi
     * cung so tien. Dung de phat hien Idempotency-Key bi dung lai cho mot lan nap khac.
     */
    public boolean isRecordedBy(List<LedgerEntry> entries) {
        return entries.size() == EXPECTED_ENTRIES
                && entries.stream().anyMatch(entry -> matches(entry, CASH_VAULT, EntryDirection.DEBIT))
                && entries.stream().anyMatch(entry -> matches(entry, accountNo, EntryDirection.CREDIT));
    }

    /** Dong Co vao tai khoan khach hang trong {@code entries} cua journal nap tien nay. */
    public LedgerEntry creditEntryOf(List<LedgerEntry> entries) {
        return entries.stream()
                .filter(entry -> matches(entry, accountNo, EntryDirection.CREDIT))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Deposit journal has no credit entry"));
    }

    private boolean matches(LedgerEntry entry, String expectedAccountNo, EntryDirection expectedDirection) {
        return entry.getAccountNo().equals(expectedAccountNo)
                && entry.getDirection() == expectedDirection
                && entry.getAmount().compareTo(amount) == 0;
    }
}
