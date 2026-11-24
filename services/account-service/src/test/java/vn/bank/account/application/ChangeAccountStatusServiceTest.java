package vn.bank.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.account.domain.AccountHolder;
import vn.bank.account.domain.AccountStatus;
import vn.bank.account.domain.AccountStatusChange;
import vn.bank.account.domain.AccountStatusHistory;
import vn.bank.account.domain.AccountType;
import vn.bank.account.infrastructure.messaging.AccountStatusChangedProducer;
import vn.bank.account.infrastructure.persistence.AccountRepository;
import vn.bank.account.infrastructure.persistence.AccountStatusHistoryRepository;
import vn.bank.common.error.BusinessException;

class ChangeAccountStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String ACCOUNT_NO = "100000000007";
    private static final String CIF = "C000000001";
    private static final String REASON = "Nghi ngo gian lan";

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountStatusHistoryRepository historyRepository = mock(AccountStatusHistoryRepository.class);
    private final AccountStatusChangedProducer producer = mock(AccountStatusChangedProducer.class);
    private final ChangeAccountStatusService service = new ChangeAccountStatusService(
            accountRepository, new AccountStatusUpdater(historyRepository, producer, CLOCK));

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
    void should_lockAccountAndSaveHistory_when_adminLocksActiveAccount() {
        // given
        givenAccount();

        // when
        var account = service.changeStatus(ACCOUNT_NO, AccountStatus.LOCKED, REASON);

        // then
        assertThat(account.getStatus()).isEqualTo(AccountStatus.LOCKED);
        var history = ArgumentCaptor.forClass(AccountStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAccountNo()).isEqualTo(ACCOUNT_NO);
        assertThat(history.getValue().getPreviousStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(history.getValue().getStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(history.getValue().getReason()).isEqualTo(REASON);
        assertThat(history.getValue().getChangedAt()).isEqualTo(NOW);
        verify(accountRepository).flush();
    }

    @Test
    void should_writeAccountStatusChangedToOutboxInsideTransaction_when_statusChanged() {
        // given
        givenAccount();

        // when
        service.changeStatus(ACCOUNT_NO, AccountStatus.LOCKED, REASON);

        // then: ghi outbox ngay trong transaction, khong doi afterCommit
        verify(producer)
                .send(new AccountStatusChange(
                        ACCOUNT_NO, CIF, AccountStatus.ACTIVE, AccountStatus.LOCKED, REASON, NOW));
    }

    @Test
    void should_throwAccountNotFound_when_accountDoesNotExist() {
        // given
        when(accountRepository.findById(ACCOUNT_NO)).thenReturn(Optional.empty());

        // when
        var thrown = catchThrowable(() -> service.changeStatus(ACCOUNT_NO, AccountStatus.LOCKED, REASON));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
        verify(historyRepository, never()).save(any());
    }

    @Test
    void should_notSaveHistoryNorPublish_when_transitionInvalid() {
        // given
        givenAccount();
        service.changeStatus(ACCOUNT_NO, AccountStatus.CLOSED, REASON);
        TransactionSynchronizationManager.clear();
        TransactionSynchronizationManager.initSynchronization();

        // when
        var thrown = catchThrowable(() -> service.changeStatus(ACCOUNT_NO, AccountStatus.ACTIVE, REASON));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.INVALID_STATUS_TRANSITION);
        // chi co dong history cua lan dong tai khoan o buoc given
        verify(historyRepository).save(any());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void should_throwConcurrentModification_when_accountUpdatedByAnotherRequest() {
        // given
        givenAccount();
        doThrow(new ObjectOptimisticLockingFailureException(Account.class, ACCOUNT_NO))
                .when(accountRepository)
                .flush();

        // when
        var thrown = catchThrowable(() -> service.changeStatus(ACCOUNT_NO, AccountStatus.LOCKED, REASON));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.CONCURRENT_MODIFICATION);
    }

    private void givenAccount() {
        var account = Account.open(ACCOUNT_NO, new AccountHolder(CIF, "ACTIVE"), AccountType.CURRENT, 0, CLOCK);
        when(accountRepository.findById(ACCOUNT_NO)).thenReturn(Optional.of(account));
    }
}
