package vn.bank.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.account.domain.AccountNumberMasker;
import vn.bank.account.domain.AccountStatus;
import vn.bank.account.infrastructure.persistence.AccountRepository;
import vn.bank.common.error.BusinessException;

/** ADMIN khoa, mo khoa, dong tai khoan. */
@Service
@RequiredArgsConstructor
public class ChangeAccountStatusService {

    private final AccountRepository accountRepository;
    private final AccountStatusUpdater accountStatusUpdater;

    /**
     * Tai khoan khong ton tai → {@code ACCOUNT_NOT_FOUND}; bi request khac sua dong thoi →
     * {@code CONCURRENT_MODIFICATION}.
     */
    @Transactional(timeout = CreateAccountService.WRITE_TIMEOUT_SECONDS)
    public Account changeStatus(String accountNo, AccountStatus target, String reason) {
        var account = accountRepository
                .findById(accountNo)
                .orElseThrow(() ->
                        new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND, "Account not found: " + accountNo));
        accountStatusUpdater.changeStatus(account, target, reason);
        AccountFlusher.flush(accountRepository, "Account " + AccountNumberMasker.mask(accountNo));
        return account;
    }
}
