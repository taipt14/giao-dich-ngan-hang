package vn.bank.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.account.domain.AccountHolder;
import vn.bank.account.domain.AccountType;
import vn.bank.account.domain.BalanceChange;
import vn.bank.account.domain.EntryDirection;
import vn.bank.account.domain.Journal;
import vn.bank.account.domain.LedgerEntry;
import vn.bank.account.domain.PostingLine;
import vn.bank.account.domain.ReferenceType;
import vn.bank.account.infrastructure.cache.BalanceCache;
import vn.bank.account.infrastructure.messaging.BalanceChangedProducer;
import vn.bank.account.infrastructure.persistence.AccountRepository;
import vn.bank.account.infrastructure.persistence.JournalRepository;
import vn.bank.account.infrastructure.persistence.LedgerEntryRepository;
import vn.bank.common.error.BusinessException;

class PostingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CUSTOMER_ACCOUNT = "100000000001";
    private static final String CASH_VAULT = "CASH_VAULT";
    private static final String REFERENCE_ID = "DEP-0001";
    private static final List<PostingLine> DEPOSIT_LINES = List.of(
            new PostingLine(CASH_VAULT, EntryDirection.DEBIT, new BigDecimal("1000000.00")),
            new PostingLine(CUSTOMER_ACCOUNT, EntryDirection.CREDIT, new BigDecimal("1000000.00")));
    private static final PostingCommand DEPOSIT =
            new PostingCommand(ReferenceType.DEPOSIT, REFERENCE_ID, "Nap tien", DEPOSIT_LINES);

    private final JournalRepository journalRepository = mock(JournalRepository.class);
    private final LedgerEntryRepository ledgerEntryRepository = mock(LedgerEntryRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final BalanceCache balanceCache = mock(BalanceCache.class);
    private final BalanceChangedProducer producer = mock(BalanceChangedProducer.class);
    private final PostingService service = new PostingService(
            journalRepository,
            ledgerEntryRepository,
            accountRepository,
            new BalanceCacheEvictor(balanceCache),
            producer,
            CLOCK);

    // Gia lap transaction dang mo de service dang ky duoc callback afterCommit
    @BeforeEach
    void startTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSynchronization() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void should_writeEntriesWithBalanceAfter_when_journalBalanced() {
        // given
        givenNoJournal();
        givenSavedJournalId(11L);
        givenAccounts(customerAccount(), glAccount());

        // when
        var result = service.post(DEPOSIT);

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.journal().getId()).isEqualTo(11L);
        assertThat(result.entries())
                .extracting(LedgerEntry::getAccountNo, LedgerEntry::getBalanceAfter)
                .containsExactly(
                        tuple(CASH_VAULT, new BigDecimal("-1000000.00")),
                        tuple(CUSTOMER_ACCOUNT, new BigDecimal("1000000.00")));
        verify(ledgerEntryRepository).saveAll(result.entries());
        verify(accountRepository).flush();
    }

    @Test
    void should_evictCacheAndPublishBalanceChanged_when_committed() {
        // given
        givenNoJournal();
        givenSavedJournalId(12L);
        givenAccounts(customerAccount(), glAccount());

        // when
        service.post(DEPOSIT);
        verify(producer, never()).send(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        // then: xoa cache moi tai khoan bi doi, event chi cho tai khoan khach hang
        verify(balanceCache).evict(CUSTOMER_ACCOUNT);
        verify(balanceCache).evict(CASH_VAULT);
        var change = ArgumentCaptor.forClass(BalanceChange.class);
        verify(producer).send(change.capture());
        assertThat(change.getValue().accountNo()).isEqualTo(CUSTOMER_ACCOUNT);
        assertThat(change.getValue().direction()).isEqualTo(EntryDirection.CREDIT);
        assertThat(change.getValue().amount()).isEqualByComparingTo("1000000.00");
        assertThat(change.getValue().balanceAfter()).isEqualByComparingTo("1000000.00");
    }

    @Test
    void should_returnExistingJournalWithoutWriting_when_referenceAlreadyPosted() {
        // given
        var existing = Journal.create(ReferenceType.DEPOSIT, REFERENCE_ID, "Nap tien", DEPOSIT_LINES, NOW);
        ReflectionTestUtils.setField(existing, "id", 5L);
        when(journalRepository.findByReferenceIdAndReferenceType(REFERENCE_ID, ReferenceType.DEPOSIT))
                .thenReturn(Optional.of(existing));

        // when
        var result = service.post(DEPOSIT);

        // then
        assertThat(result.created()).isFalse();
        assertThat(result.journal()).isSameAs(existing);
        verify(ledgerEntryRepository).findByJournalIdOrderByIdAsc(5L);
        verify(journalRepository, never()).saveAndFlush(any());
        verify(accountRepository, never()).findAllForUpdate(anyCollection());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void should_rejectWithoutWriting_when_journalNotBalanced() {
        // given
        givenNoJournal();
        var command = new PostingCommand(
                ReferenceType.DEPOSIT,
                REFERENCE_ID,
                null,
                List.of(
                        new PostingLine(CASH_VAULT, EntryDirection.DEBIT, new BigDecimal("100.00")),
                        new PostingLine(CUSTOMER_ACCOUNT, EntryDirection.CREDIT, new BigDecimal("90.00"))));

        // when
        var thrown = catchThrowable(() -> service.post(command));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.JOURNAL_NOT_BALANCED);
        verify(journalRepository, never()).saveAndFlush(any());
    }

    @Test
    void should_rejectAccountNotFound_when_lineAccountDoesNotExist() {
        // given
        givenNoJournal();
        givenSavedJournalId(13L);
        givenAccounts(glAccount());

        // when
        var thrown = catchThrowable(() -> service.post(DEPOSIT));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
        verify(ledgerEntryRepository, never()).saveAll(any());
    }

    private void givenNoJournal() {
        when(journalRepository.findByReferenceIdAndReferenceType(any(), any())).thenReturn(Optional.empty());
    }

    private void givenSavedJournalId(long id) {
        when(journalRepository.saveAndFlush(any(Journal.class))).thenAnswer(invocation -> {
            Journal journal = invocation.getArgument(0);
            ReflectionTestUtils.setField(journal, "id", id);
            return journal;
        });
    }

    private void givenAccounts(Account... accounts) {
        when(accountRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(accounts));
    }

    private static Account customerAccount() {
        return Account.open(CUSTOMER_ACCOUNT, new AccountHolder("C000000001", "ACTIVE"), AccountType.CURRENT, 0, CLOCK);
    }

    // Tai khoan GL chi tao bang migration, test dung reflection de co entity GL
    private static Account glAccount() {
        var account = customerAccount();
        ReflectionTestUtils.setField(account, "accountNo", CASH_VAULT);
        ReflectionTestUtils.setField(account, "cif", null);
        ReflectionTestUtils.setField(account, "accountType", AccountType.GL);
        return account;
    }
}
