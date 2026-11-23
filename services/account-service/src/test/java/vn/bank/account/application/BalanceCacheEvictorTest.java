package vn.bank.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.bank.account.infrastructure.cache.BalanceCache;

class BalanceCacheEvictorTest {

    private static final String ACCOUNT_NO = "100000000001";

    private final BalanceCache balanceCache = mock(BalanceCache.class);
    private final BalanceCacheEvictor evictor = new BalanceCacheEvictor(balanceCache);

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void should_evictCacheOnlyAfterCommit_when_balanceChangedInTransaction() {
        // given
        TransactionSynchronizationManager.initSynchronization();

        // when
        evictor.evictAfterCommit(ACCOUNT_NO);

        // then
        verify(balanceCache, never()).evict(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(balanceCache).evict(ACCOUNT_NO);
    }

    @Test
    void should_keepCache_when_transactionRolledBack() {
        // given
        TransactionSynchronizationManager.initSynchronization();

        // when
        evictor.evictAfterCommit(ACCOUNT_NO);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        // then
        verify(balanceCache, never()).evict(any());
    }

    @Test
    void should_throwIllegalState_when_calledOutsideTransaction() {
        // when
        var thrown = catchThrowable(() -> evictor.evictAfterCommit(ACCOUNT_NO));

        // then
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        verify(balanceCache, never()).evict(any());
    }
}
