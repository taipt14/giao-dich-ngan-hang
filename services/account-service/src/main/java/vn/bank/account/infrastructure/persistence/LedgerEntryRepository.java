package vn.bank.account.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.bank.account.domain.LedgerEntry;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    // Dung index IX_LEDGER_ENTRY_JOURNAL_ID; ENTRY_ID tang theo thu tu ghi nen sap theo id = thu tu dong
    List<LedgerEntry> findByJournalIdOrderByIdAsc(Long journalId);
}
