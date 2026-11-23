package vn.bank.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.bank.account.infrastructure.cache.BalanceCache;

/**
 * Xoa cache so du sau khi transaction thay doi so du commit thanh cong. Moi use case doi so du (posting, nap tien...)
 * phai goi {@link #evictAfterCommit} ben trong transaction cua no.
 */
@Component
@RequiredArgsConstructor
public class BalanceCacheEvictor {

    private final BalanceCache balanceCache;

    /**
     * Xoa truoc khi commit thi request doc dong thoi co the nap lai so du cu vao cache; xoa khi rollback thi mat cache vo
     * ich. Vi vay chi xoa trong {@code afterCommit}.
     *
     * @throws IllegalStateException neu goi ngoai transaction: khong co commit nao de cho, cache se khong bao gio bi xoa
     */
    public void evictAfterCommit(String accountNo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Balance cache eviction requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                balanceCache.evict(accountNo);
            }
        });
    }
}
