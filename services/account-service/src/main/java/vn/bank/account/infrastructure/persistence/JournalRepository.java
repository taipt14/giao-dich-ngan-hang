package vn.bank.account.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.bank.account.domain.Journal;
import vn.bank.account.domain.ReferenceType;

public interface JournalRepository extends JpaRepository<Journal, Long> {

    // Dung index UK_JOURNAL_REFERENCE_ID_TYPE: posting idempotent theo (REFERENCE_TYPE, REFERENCE_ID)
    Optional<Journal> findByReferenceIdAndReferenceType(String referenceId, ReferenceType referenceType);
}
