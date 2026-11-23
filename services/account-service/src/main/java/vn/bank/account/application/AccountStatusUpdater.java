package vn.bank.account.application;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountStatus;
import vn.bank.account.domain.AccountStatusChange;
import vn.bank.account.domain.AccountStatusHistory;
import vn.bank.account.infrastructure.messaging.AccountStatusChangedProducer;
import vn.bank.account.infrastructure.persistence.AccountStatusHistoryRepository;

/**
 * Buoc chung cua moi lan doi trang thai tai khoan (ADMIN goi API hoac khach hang bi khoa): doi trang thai, ghi
 * {@code ACCOUNT_STATUS_HISTORY}, ghi {@code AccountStatusChanged} vao outbox. Phai goi trong transaction cua use case.
 *
 * <p>Khong xoa cache {@code balance:{accountNo}}: cache chi luu so du, khong luu trang thai.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AccountStatusUpdater {

    private final AccountStatusHistoryRepository accountStatusHistoryRepository;
    private final AccountStatusChangedProducer accountStatusChangedProducer;
    private final Clock clock;

    AccountStatusChange changeStatus(Account account, AccountStatus target, String reason) {
        var change = account.changeStatus(target, reason, clock);
        accountStatusHistoryRepository.save(AccountStatusHistory.of(change));
        // Outbox (BANK-046): ghi event cung transaction cua use case, rollback thi khong co event nao duoc gui
        accountStatusChangedProducer.send(change);
        logAfterCommit(change);
        return change;
    }

    // Moc nghiep vu chi log khi da commit
    private void logAfterCommit(AccountStatusChange change) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info(
                        "Account {} status changed from {} to {}",
                        change.maskedAccountNo(),
                        change.previousStatus(),
                        change.status());
            }
        });
    }
}
