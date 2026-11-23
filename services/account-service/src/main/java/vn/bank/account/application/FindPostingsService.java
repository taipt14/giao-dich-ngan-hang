package vn.bank.account.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.bank.account.domain.LedgerEntry;
import vn.bank.account.infrastructure.persistence.JournalRepository;
import vn.bank.account.infrastructure.persistence.LedgerEntryRepository;

/**
 * Tra soat posting theo ma nghiep vu goc: service goi posting bi timeout (lenh UNKNOWN, BANK-045) doc lai de biet
 * journal da ghi hay chua. Mot referenceId co the co nhieu journal khac loai (vi du TRANSFER va REVERSAL cua cung
 * transferId, docs/ledger.md 4.5).
 */
@Service
@RequiredArgsConstructor
public class FindPostingsService {

    private final JournalRepository journalRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    /** Cac journal cua {@code referenceId} theo thu tu ghi, kem dong so cai; chua co journal nao thi rong. */
    @Transactional(readOnly = true)
    public List<PostingResult> findByReferenceId(String referenceId) {
        var journals = journalRepository.findByReferenceIdOrderByIdAsc(referenceId);
        if (journals.isEmpty()) {
            return List.of();
        }
        var entriesByJournalId = new LinkedHashMap<Long, List<LedgerEntry>>();
        journals.forEach(journal -> entriesByJournalId.put(journal.getId(), new ArrayList<>()));
        for (var entry : ledgerEntryRepository.findByJournalIdInOrderByIdAsc(entriesByJournalId.keySet())) {
            entriesByJournalId.get(entry.getJournalId()).add(entry);
        }
        return journals.stream()
                .map(journal -> new PostingResult(journal, entriesByJournalId.get(journal.getId()), false))
                .toList();
    }
}
