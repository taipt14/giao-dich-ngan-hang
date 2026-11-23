package vn.bank.account.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.account.domain.BankZone;
import vn.bank.account.domain.Journal;
import vn.bank.account.domain.LedgerEntry;
import vn.bank.account.domain.StatementLine;
import vn.bank.account.domain.StatementPeriod;
import vn.bank.account.infrastructure.persistence.AccountRepository;
import vn.bank.account.infrastructure.persistence.JournalRepository;
import vn.bank.account.infrastructure.persistence.LedgerEntryRepository;
import vn.bank.common.error.BusinessException;

/**
 * Sao ke tai khoan khach hang, phan trang keyset theo ENTRY_ID giam dan: trang sau lay cac dong co ENTRY_ID nho hon
 * {@code nextCursor} cua trang truoc, nen dong moi ghi them khong lam lech trang (khac phan trang theo offset).
 */
@Service
@RequiredArgsConstructor
public class GetStatementService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final JournalRepository journalRepository;
    private final Clock clock;

    /**
     * Khoang ngay khong hop le → {@code STATEMENT_RANGE_INVALID}/{@code STATEMENT_RANGE_TOO_LARGE}; tai khoan khong ton
     * tai → {@code ACCOUNT_NOT_FOUND}; khong thuoc {@code cif} (ke ca tai khoan GL) → {@code ACCOUNT_ACCESS_DENIED}.
     */
    @Transactional(readOnly = true)
    public StatementPage getStatement(StatementQuery query) {
        var today = LocalDate.now(clock.withZone(BankZone.ZONE));
        var period = StatementPeriod.resolve(query.from(), query.to(), today);
        requireOwnedAccount(query.cif(), query.accountNo());
        // Doc them mot dong de biet con trang sau hay khong
        var entries = ledgerEntryRepository.findStatementEntries(
                query.accountNo(),
                period.startInclusive(),
                period.endExclusive(),
                query.cursor() != null ? query.cursor() : Long.MAX_VALUE,
                query.size() + 1);
        var hasMore = entries.size() > query.size();
        var pageEntries = hasMore ? entries.subList(0, query.size()) : entries;
        var items = toLines(pageEntries);
        var nextCursor = hasMore ? items.getLast().entryId() : null;
        return new StatementPage(items, query.size(), nextCursor);
    }

    private void requireOwnedAccount(String cif, String accountNo) {
        var account = accountRepository
                .findById(accountNo)
                .orElseThrow(() ->
                        new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND, "Account not found: " + accountNo));
        if (!account.isOwnedBy(cif)) {
            throw new BusinessException(
                    AccountErrorCode.ACCOUNT_ACCESS_DENIED,
                    "Account " + account.getMaskedAccountNo() + " does not belong to the authenticated customer");
        }
    }

    // Mot truy van lay journal cua ca trang (toi da size dong), tranh N+1
    private List<StatementLine> toLines(List<LedgerEntry> entries) {
        var journalIds =
                entries.stream().map(LedgerEntry::getJournalId).distinct().toList();
        Map<Long, Journal> journals = journalRepository.findAllById(journalIds).stream()
                .collect(Collectors.toMap(Journal::getId, Function.identity()));
        var lines = new ArrayList<StatementLine>(entries.size());
        for (var entry : entries) {
            lines.add(StatementLine.of(entry, journals.get(entry.getJournalId())));
        }
        return lines;
    }
}
