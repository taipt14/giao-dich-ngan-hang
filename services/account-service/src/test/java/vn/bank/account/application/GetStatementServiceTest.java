package vn.bank.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.account.domain.AccountHolder;
import vn.bank.account.domain.AccountType;
import vn.bank.account.domain.EntryDirection;
import vn.bank.account.domain.Journal;
import vn.bank.account.domain.LedgerEntry;
import vn.bank.account.domain.PostingLine;
import vn.bank.account.domain.ReferenceType;
import vn.bank.account.domain.StatementLine;
import vn.bank.account.infrastructure.persistence.AccountRepository;
import vn.bank.account.infrastructure.persistence.JournalRepository;
import vn.bank.account.infrastructure.persistence.LedgerEntryRepository;
import vn.bank.common.error.BusinessException;

class GetStatementServiceTest {

    // 2026-11-23 01:00 gio Viet Nam (UTC+7) nhung van la 2026-11-22 theo UTC
    private static final Instant NOW = Instant.parse("2026-11-22T18:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CIF = "C000000001";
    private static final String ACCOUNT_NO = "100000000001";
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final LedgerEntryRepository ledgerEntryRepository = mock(LedgerEntryRepository.class);
    private final JournalRepository journalRepository = mock(JournalRepository.class);
    private final GetStatementService service =
            new GetStatementService(accountRepository, ledgerEntryRepository, journalRepository, CLOCK);

    @Test
    void should_queryLast30VietnamDaysWithOneExtraRow_when_noDatesAndNoCursor() {
        // given
        givenAccount(CIF);
        givenEntries(List.of());

        // when
        var page = service.getStatement(new StatementQuery(CIF, ACCOUNT_NO, null, null, null, 20));

        // then: hom nay 2026-11-23 gio VN → tu 2026-10-25 00:00 VN den truoc 2026-11-24 00:00 VN
        verify(ledgerEntryRepository)
                .findStatementEntries(
                        ACCOUNT_NO,
                        Instant.parse("2026-10-24T17:00:00Z"),
                        Instant.parse("2026-11-23T17:00:00Z"),
                        Long.MAX_VALUE,
                        21);
        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void should_returnNextCursorAsLastEntryId_when_moreEntriesThanSize() {
        // given
        givenAccount(CIF);
        givenEntries(entries(30L, 20L, 10L));

        // when
        var page = service.getStatement(new StatementQuery(CIF, ACCOUNT_NO, null, null, 40L, 2));

        // then
        verify(ledgerEntryRepository)
                .findStatementEntries(eq(ACCOUNT_NO), any(Instant.class), any(Instant.class), eq(40L), eq(3));
        assertThat(page.items()).extracting(StatementLine::entryId).containsExactly(30L, 20L);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.nextCursor()).isEqualTo(20L);
    }

    @Test
    void should_returnNullNextCursor_when_lastPage() {
        // given
        givenAccount(CIF);
        givenEntries(entries(20L, 10L));

        // when
        var page = service.getStatement(new StatementQuery(CIF, ACCOUNT_NO, null, null, null, 2));

        // then
        assertThat(page.items()).extracting(StatementLine::entryId).containsExactly(20L, 10L);
        assertThat(page.items()).extracting(StatementLine::description).containsOnly("Chuyen tien");
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void should_rejectAccountAccessDenied_when_accountBelongsToOtherCustomer() {
        // given
        givenAccount("C000000002");

        // when
        var thrown =
                catchThrowable(() -> service.getStatement(new StatementQuery(CIF, ACCOUNT_NO, null, null, null, 20)));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_ACCESS_DENIED);
        verifyNoInteractions(ledgerEntryRepository);
    }

    @Test
    void should_rejectAccountNotFound_when_accountDoesNotExist() {
        // given
        when(accountRepository.findById(ACCOUNT_NO)).thenReturn(Optional.empty());

        // when
        var thrown =
                catchThrowable(() -> service.getStatement(new StatementQuery(CIF, ACCOUNT_NO, null, null, null, 20)));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    void should_rejectStatementRangeTooLargeWithoutQueryingDb_when_rangeExceeds90Days() {
        // given
        var from = LocalDate.parse("2026-01-01");
        var to = LocalDate.parse("2026-04-01");

        // when
        var thrown =
                catchThrowable(() -> service.getStatement(new StatementQuery(CIF, ACCOUNT_NO, from, to, null, 20)));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.STATEMENT_RANGE_TOO_LARGE);
        verifyNoInteractions(accountRepository, ledgerEntryRepository);
    }

    private void givenAccount(String cif) {
        var account = Account.open(ACCOUNT_NO, new AccountHolder(cif, "ACTIVE"), AccountType.CURRENT, 0, CLOCK);
        when(accountRepository.findById(ACCOUNT_NO)).thenReturn(Optional.of(account));
    }

    private void givenEntries(List<LedgerEntry> entries) {
        when(ledgerEntryRepository.findStatementEntries(
                        eq(ACCOUNT_NO), any(Instant.class), any(Instant.class), anyLong(), anyInt()))
                .thenReturn(entries);
    }

    // Moi dong mot journal rieng (id = entryId + 1000), deu la tien vao tai khoan
    private List<LedgerEntry> entries(Long... entryIds) {
        var entries = new ArrayList<LedgerEntry>();
        var journals = new ArrayList<Journal>();
        var account = Account.open(ACCOUNT_NO, new AccountHolder(CIF, "ACTIVE"), AccountType.CURRENT, 0, CLOCK);
        for (var entryId : entryIds) {
            var journal = journal(entryId + 1000);
            var entry = LedgerEntry.post(journal, account, new PostingLine(ACCOUNT_NO, EntryDirection.CREDIT, AMOUNT));
            ReflectionTestUtils.setField(entry, "id", entryId);
            journals.add(journal);
            entries.add(entry);
        }
        when(journalRepository.findAllById(anyIterable())).thenReturn(journals);
        return entries;
    }

    private static Journal journal(long id) {
        var lines = List.of(
                new PostingLine("CASH_VAULT", EntryDirection.DEBIT, AMOUNT),
                new PostingLine(ACCOUNT_NO, EntryDirection.CREDIT, AMOUNT));
        var journal = Journal.create(ReferenceType.TRANSFER, "TRF-" + id, "Chuyen tien", lines, NOW);
        ReflectionTestUtils.setField(journal, "id", id);
        return journal;
    }
}
