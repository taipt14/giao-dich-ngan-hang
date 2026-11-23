package vn.bank.account.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.account.domain.AccountNumberMasker;
import vn.bank.account.domain.BalanceChange;
import vn.bank.account.domain.Journal;
import vn.bank.account.domain.LedgerEntry;
import vn.bank.account.domain.PostingLine;
import vn.bank.account.domain.ReferenceType;
import vn.bank.account.infrastructure.messaging.BalanceChangedProducer;
import vn.bank.account.infrastructure.persistence.AccountRepository;
import vn.bank.account.infrastructure.persistence.JournalRepository;
import vn.bank.account.infrastructure.persistence.LedgerEntryRepository;
import vn.bank.common.error.BusinessException;
import vn.bank.common.error.TechnicalException;

/**
 * Diem duy nhat duoc thay doi so du (docs/ledger.md). Trong mot transaction: tao JOURNAL, khoa cac tai khoan lien
 * quan, cap nhat so du, ghi LEDGER_ENTRY kem BALANCE_AFTER. Mot dong vi pham quy tac thi toan bo rollback.
 *
 * <p>Dung khoa bi quan ({@code SELECT ... FOR UPDATE}) thay vi chi dua vao {@code VERSION}: tai khoan nong (nhieu
 * posting cung luc) voi khoa lac quan se lien tuc xung dot, client phai retry nhieu lan; con khoa bi quan cho cac
 * posting xep hang, kiem tra so du tren gia tri moi nhat va BALANCE_AFTER khong bi chen. Cho khoa qua
 * {@link AccountRepository#LOCK_TIMEOUT_MILLIS} → 503 {@code ACCOUNT_BUSY}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostingService {

    private final JournalRepository journalRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;
    private final BalanceCacheEvictor balanceCacheEvictor;
    private final BalanceChangedProducer balanceChangedProducer;
    private final Clock clock;

    /**
     * Ghi journal cho {@code command}. Reference da co journal: tra lai journal cu, khong kiem tra lai va khong ghi
     * them. Hai posting dong thoi cung reference: ben sau gap {@code UK_JOURNAL_REFERENCE_ID_TYPE} khi INSERT JOURNAL
     * ({@code DataIntegrityViolationException}, xu ly o {@link SubmitPostingService}).
     */
    @Transactional(timeout = CreateAccountService.WRITE_TIMEOUT_SECONDS)
    public PostingResult post(PostingCommand command) {
        var existing =
                journalRepository.findByReferenceIdAndReferenceType(command.referenceId(), command.referenceType());
        if (existing.isPresent()) {
            return toExistingResult(existing.get());
        }
        var journal = Journal.create(
                command.referenceType(),
                command.referenceId(),
                command.description(),
                command.lines(),
                clock.instant());
        // Flush ngay: trung reference thi loi o day, truoc khi khoa tai khoan
        journalRepository.saveAndFlush(journal);
        var accounts = lockAccounts(command.lines());
        var entries = new ArrayList<LedgerEntry>();
        for (var line : command.lines()) {
            entries.add(LedgerEntry.post(journal, accounts.get(line.accountNo()), line));
        }
        ledgerEntryRepository.saveAll(entries);
        AccountFlusher.flush(accountRepository, "Accounts of journal " + journal.getId());
        accounts.keySet().forEach(balanceCacheEvictor::evictAfterCommit);
        publishAfterCommit(journal, BalanceChange.of(journal, command.lines(), accounts));
        return new PostingResult(journal, entries, true);
    }

    @Transactional(readOnly = true)
    public Optional<PostingResult> findPosted(ReferenceType referenceType, String referenceId) {
        return journalRepository
                .findByReferenceIdAndReferenceType(referenceId, referenceType)
                .map(this::toExistingResult);
    }

    private PostingResult toExistingResult(Journal journal) {
        return new PostingResult(journal, ledgerEntryRepository.findByJournalIdOrderByIdAsc(journal.getId()), false);
    }

    /**
     * Khoa tung tai khoan theo {@code ACCOUNT_NO} tang dan: moi posting xin khoa theo cung mot thu tu nen khong the co
     * vong cho nhau (A cho B, B cho A) gay deadlock.
     */
    private Map<String, Account> lockAccounts(List<PostingLine> lines) {
        var accountNos = new TreeSet<String>();
        lines.forEach(line -> accountNos.add(line.accountNo()));
        var accounts = new LinkedHashMap<String, Account>();
        for (var accountNo : accountNos) {
            accounts.put(accountNo, lockAccount(accountNo));
        }
        return accounts;
    }

    private Account lockAccount(String accountNo) {
        try {
            return accountRepository
                    .findForUpdate(accountNo)
                    .orElseThrow(() -> new BusinessException(
                            AccountErrorCode.ACCOUNT_NOT_FOUND, "Account not found: " + accountNo));
        } catch (PessimisticLockingFailureException e) {
            throw new TechnicalException(
                    AccountErrorCode.ACCOUNT_BUSY,
                    "Account " + AccountNumberMasker.mask(accountNo) + " is busy, please retry",
                    e);
        }
    }

    // Muc 8 convention: khong gui Kafka trong transaction DB. Tam gui truc tiep sau commit, BANK-046 thay bang outbox
    private void publishAfterCommit(Journal journal, List<BalanceChange> changes) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info(
                        "Posted journal {} for {} {}, {} customer balance changes",
                        journal.getId(),
                        journal.getReferenceType(),
                        journal.getReferenceId(),
                        changes.size());
                changes.forEach(balanceChangedProducer::send);
            }
        });
    }
}
