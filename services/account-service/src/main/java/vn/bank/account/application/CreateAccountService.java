package vn.bank.account.application;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountHolder;
import vn.bank.account.domain.AccountNumber;
import vn.bank.account.domain.AccountType;
import vn.bank.account.infrastructure.messaging.AccountOpenedProducer;
import vn.bank.account.infrastructure.persistence.AccountRepository;

/**
 * Phan ghi DB cua viec mo tai khoan, tach khoi {@link OpenAccountService} de loi goi REST sang customer-service nam
 * ngoai transaction (muc 8 convention).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateAccountService {

    /** Timeout transaction ghi tai khoan (muc 8 convention). */
    static final int WRITE_TIMEOUT_SECONDS = 10;

    private final AccountRepository accountRepository;
    private final AccountOpenedProducer accountOpenedProducer;
    private final Clock clock;

    // Gioi han 3 tai khoan kiem tra bang dem trong transaction: 2 request dong thoi cho cung CIF van co the vuot
    @Transactional(timeout = WRITE_TIMEOUT_SECONDS)
    public Account create(AccountHolder holder, AccountType accountType) {
        var existingAccounts = accountRepository.countByCifAndAccountType(holder.cif(), accountType);
        var accountNo = AccountNumber.fromSequence(accountRepository.nextAccountNoSequence());
        var account = accountRepository.save(Account.open(accountNo, holder, accountType, existingAccounts, clock));
        // Outbox (BANK-046): ghi event cung transaction, rollback thi khong co event nao duoc gui
        accountOpenedProducer.send(account);
        logAfterCommit(account);
        return account;
    }

    // Moc nghiep vu chi log khi da commit
    private void logAfterCommit(Account account) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Account {} opened for customer {}", account.getMaskedAccountNo(), account.getCif());
            }
        });
    }
}
