package vn.bank.account.application;

import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.bank.account.domain.AccountStatus;
import vn.bank.account.infrastructure.persistence.AccountRepository;
import vn.bank.account.infrastructure.persistence.ProcessedMessageRepository;
import vn.bank.common.events.customer.CustomerStatusChanged;

/**
 * Xu ly {@code CustomerStatusChanged}: khach hang bi LOCKED thi khoa moi tai khoan ACTIVE cua khach hang do. Khach
 * hang duoc mo khoa thi khong tu mo khoa tai khoan (ADMIN mo tung tai khoan).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LockCustomerAccountsService {

    /** Ten consumer ghi vao {@code PROCESSED_MESSAGE}. */
    static final String CONSUMER = "account-service.lock-customer-accounts";

    static final String LOCKED_CUSTOMER_STATUS = "LOCKED";
    static final String REASON_PREFIX = "Customer locked: ";

    private final AccountRepository accountRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final AccountStatusUpdater accountStatusUpdater;
    private final Clock clock;

    /** Ghi {@code PROCESSED_MESSAGE} cung transaction voi viec khoa tai khoan: loi giua chung thi ca hai rollback. */
    @Transactional(timeout = CreateAccountService.WRITE_TIMEOUT_SECONDS)
    public void handle(UUID eventId, CustomerStatusChanged event) {
        if (!processedMessageRepository.markProcessed(eventId, CONSUMER, clock.instant())) {
            log.warn("Skip duplicate CustomerStatusChanged {} for customer {}", eventId, event.cif());
            return;
        }
        if (!LOCKED_CUSTOMER_STATUS.equals(event.status())) {
            return;
        }
        var accounts = accountRepository.findByCifAndStatusOrderByAccountNoAsc(event.cif(), AccountStatus.ACTIVE);
        for (var account : accounts) {
            accountStatusUpdater.changeStatus(account, AccountStatus.LOCKED, REASON_PREFIX + event.reason());
        }
        AccountFlusher.flush(accountRepository, "Accounts of customer " + event.cif());
        log.info("Locked {} active accounts of customer {}", accounts.size(), event.cif());
    }
}
