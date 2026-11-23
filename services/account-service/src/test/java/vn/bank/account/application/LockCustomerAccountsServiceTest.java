package vn.bank.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountHolder;
import vn.bank.account.domain.AccountStatus;
import vn.bank.account.domain.AccountStatusHistory;
import vn.bank.account.domain.AccountType;
import vn.bank.account.infrastructure.messaging.AccountStatusChangedProducer;
import vn.bank.account.infrastructure.persistence.AccountRepository;
import vn.bank.account.infrastructure.persistence.AccountStatusHistoryRepository;
import vn.bank.account.infrastructure.persistence.ProcessedMessageRepository;
import vn.bank.common.events.customer.CustomerStatusChanged;

class LockCustomerAccountsServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CIF = "C000000001";
    private static final UUID EVENT_ID = UUID.fromString("7b0f7c3e-1d2a-4f7e-9a51-0c6a4f3b2d10");
    private static final CustomerStatusChanged CUSTOMER_LOCKED =
            new CustomerStatusChanged(CIF, "ACTIVE", "LOCKED", "Nghi ngo gian lan", NOW);

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final ProcessedMessageRepository processedMessageRepository = mock(ProcessedMessageRepository.class);
    private final AccountStatusHistoryRepository historyRepository = mock(AccountStatusHistoryRepository.class);
    private final AccountStatusChangedProducer producer = mock(AccountStatusChangedProducer.class);
    private final LockCustomerAccountsService service = new LockCustomerAccountsService(
            accountRepository,
            processedMessageRepository,
            new AccountStatusUpdater(historyRepository, producer, CLOCK),
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
    void should_lockAllActiveAccounts_when_customerLocked() {
        // given
        givenNewMessage();
        var first = givenAccount("100000000001");
        var second = givenAccount("100000000002");
        when(accountRepository.findByCifAndStatusOrderByAccountNoAsc(CIF, AccountStatus.ACTIVE))
                .thenReturn(List.of(first, second));

        // when
        service.handle(EVENT_ID, CUSTOMER_LOCKED);

        // then
        assertThat(List.of(first, second))
                .allSatisfy(account -> assertThat(account.getStatus()).isEqualTo(AccountStatus.LOCKED));
        var history = ArgumentCaptor.forClass(AccountStatusHistory.class);
        verify(historyRepository, times(2)).save(history.capture());
        assertThat(history.getAllValues())
                .extracting(AccountStatusHistory::getReason)
                .containsOnly("Customer locked: Nghi ngo gian lan");
        verify(accountRepository).flush();
        // moi tai khoan mot event AccountStatusChanged sau commit
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(2);
    }

    @Test
    void should_notLockAnyAccount_when_sameEventAlreadyProcessed() {
        // given
        when(processedMessageRepository.markProcessed(EVENT_ID, LockCustomerAccountsService.CONSUMER, NOW))
                .thenReturn(false);

        // when
        service.handle(EVENT_ID, CUSTOMER_LOCKED);

        // then
        verify(accountRepository, never()).findByCifAndStatusOrderByAccountNoAsc(anyString(), any());
        verify(historyRepository, never()).save(any());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void should_notChangeAccounts_when_customerUnlocked() {
        // given
        givenNewMessage();
        var unlocked = new CustomerStatusChanged(CIF, "LOCKED", "ACTIVE", "Da xac minh", NOW);

        // when
        service.handle(EVENT_ID, unlocked);

        // then
        verify(processedMessageRepository).markProcessed(EVENT_ID, LockCustomerAccountsService.CONSUMER, NOW);
        verify(accountRepository, never()).findByCifAndStatusOrderByAccountNoAsc(anyString(), any());
        verify(historyRepository, never()).save(any());
    }

    private void givenNewMessage() {
        when(processedMessageRepository.markProcessed(EVENT_ID, LockCustomerAccountsService.CONSUMER, NOW))
                .thenReturn(true);
    }

    private static Account givenAccount(String accountNo) {
        return Account.open(accountNo, new AccountHolder(CIF, "ACTIVE"), AccountType.CURRENT, 0, CLOCK);
    }
}
