package vn.bank.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountBalance;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.account.infrastructure.cache.BalanceCache;
import vn.bank.account.infrastructure.persistence.AccountRepository;
import vn.bank.common.error.BusinessException;

/**
 * So du de hien thi cho khach hang, cache-aside: co trong Redis thi khong doc DB, khong co thi doc DB roi ghi cache.
 * Khong mo transaction: chi mot cau doc DB va loi goi Redis khong nen giu connection DB.
 */
@Service
@RequiredArgsConstructor
public class GetAccountBalanceService {

    private final AccountRepository accountRepository;
    private final BalanceCache balanceCache;

    /** Tai khoan khong ton tai → {@code ACCOUNT_NOT_FOUND}; khong thuoc {@code cif} → {@code ACCOUNT_ACCESS_DENIED}. */
    public AccountBalance getBalance(String cif, String accountNo) {
        var balance = balanceCache.find(accountNo).orElseGet(() -> loadAndCache(accountNo));
        if (!balance.isOwnedBy(cif)) {
            throw new BusinessException(
                    AccountErrorCode.ACCOUNT_ACCESS_DENIED,
                    "Account " + accountNo + " does not belong to the authenticated customer");
        }
        return balance;
    }

    private AccountBalance loadAndCache(String accountNo) {
        var balance = accountRepository
                .findById(accountNo)
                .map(Account::toBalance)
                .orElseThrow(() ->
                        new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND, "Account not found: " + accountNo));
        balanceCache.put(balance);
        return balance;
    }
}
