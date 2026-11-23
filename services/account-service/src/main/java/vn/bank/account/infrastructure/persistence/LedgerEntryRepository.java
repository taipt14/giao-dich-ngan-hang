package vn.bank.account.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.bank.account.domain.LedgerEntry;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /**
     * Sao ke: dong cua mot tai khoan trong {@code [from, to)}, ENTRY_ID nho hon {@code beforeEntryId} (keyset), moi nhat
     * truoc. Hint ep dung {@code IX_LEDGER_ENTRY_ACC_CREATED}: voi {@code FETCH FIRST} optimizer co the chon quet nguoc
     * PK (khoi sap xep) roi loc ACCOUNT_NO, phai doc qua dong cua moi tai khoan khac. Khoang ngay toi da 90 ngay nen sap
     * xep cac dong cua mot tai khoan trong khoang do la re.
     */
    String STATEMENT_QUERY = """
            SELECT /*+ INDEX(e IX_LEDGER_ENTRY_ACC_CREATED) */ e.*
            FROM LEDGER_ENTRY e
            WHERE e.ACCOUNT_NO = :accountNo
              AND e.CREATED_AT >= :fromInclusive
              AND e.CREATED_AT < :toExclusive
              AND e.ENTRY_ID < :beforeEntryId
            ORDER BY e.ENTRY_ID DESC
            FETCH FIRST :limit ROWS ONLY""";

    // Dung index IX_LEDGER_ENTRY_JOURNAL_ID; ENTRY_ID tang theo thu tu ghi nen sap theo id = thu tu dong
    List<LedgerEntry> findByJournalIdOrderByIdAsc(Long journalId);

    @Query(value = STATEMENT_QUERY, nativeQuery = true)
    List<LedgerEntry> findStatementEntries(
            @Param("accountNo") String accountNo,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive,
            @Param("beforeEntryId") long beforeEntryId,
            @Param("limit") int limit);
}
