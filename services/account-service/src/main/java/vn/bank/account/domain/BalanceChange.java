package vn.bank.account.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bien dong so du cua mot tai khoan khach hang do mot journal gay ra (du lieu cho event {@code BalanceChanged}). Tai
 * khoan co nhieu dong trong cung journal thi gop thanh mot bien dong theo so tien rong; {@code amount} luon duong,
 * huong the hien bang {@code direction}.
 */
public record BalanceChange(
        String accountNo,
        String cif,
        Long journalId,
        ReferenceType referenceType,
        String referenceId,
        EntryDirection direction,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String currency,
        String description,
        Instant changedAt) {

    public String maskedAccountNo() {
        return AccountNumberMasker.mask(accountNo);
    }

    /**
     * Bien dong cua tung tai khoan khach hang trong {@code lines}, theo thu tu xuat hien. Tai khoan GL va tai khoan co
     * so tien rong bang 0 (No va Co bu tru het) khong co bien dong. {@code accounts}: tai khoan da ghi so, theo so tai
     * khoan.
     */
    public static List<BalanceChange> of(Journal journal, List<PostingLine> lines, Map<String, Account> accounts) {
        var netChanges = new LinkedHashMap<String, BigDecimal>();
        for (var line : lines) {
            netChanges.merge(line.accountNo(), line.signedAmount(), BigDecimal::add);
        }
        var changes = new ArrayList<BalanceChange>();
        netChanges.forEach((accountNo, net) -> {
            var account = accounts.get(accountNo);
            if (account.getAccountType().isCustomerAccount() && net.signum() != 0) {
                changes.add(of(journal, account, net));
            }
        });
        return changes;
    }

    private static BalanceChange of(Journal journal, Account account, BigDecimal net) {
        var direction = net.signum() > 0 ? EntryDirection.CREDIT : EntryDirection.DEBIT;
        return new BalanceChange(
                account.getAccountNo(),
                account.getCif(),
                journal.getId(),
                journal.getReferenceType(),
                journal.getReferenceId(),
                direction,
                net.abs(),
                account.getBalance(),
                account.getCurrency(),
                journal.getDescription(),
                journal.getCreatedAt());
    }
}
