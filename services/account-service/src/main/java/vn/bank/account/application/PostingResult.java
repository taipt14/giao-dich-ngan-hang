package vn.bank.account.application;

import java.util.List;
import vn.bank.account.domain.Journal;
import vn.bank.account.domain.LedgerEntry;

/**
 * Journal da ghi va cac dong so cai cua no theo thu tu ghi. {@code created = false}: journal da co tu truoc (posting
 * lap lai cung reference), khong ghi them gi.
 */
public record PostingResult(Journal journal, List<LedgerEntry> entries, boolean created) {

    public PostingResult {
        entries = List.copyOf(entries);
    }
}
