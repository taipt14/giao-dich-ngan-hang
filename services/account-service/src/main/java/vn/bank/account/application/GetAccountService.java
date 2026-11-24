package vn.bank.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.account.domain.AccountNumberMasker;
import vn.bank.account.infrastructure.persistence.AccountRepository;
import vn.bank.common.error.BusinessException;

/** Tra cuu mot tai khoan theo so tai khoan cho service khac (transfer-service kiem tra chu so huu, trang thai). */
@Service
@RequiredArgsConstructor
public class GetAccountService {

    private final AccountRepository accountRepository;

    /** Tai khoan khong ton tai → {@code ACCOUNT_NOT_FOUND}. */
    @Transactional(readOnly = true)
    public Account getAccount(String accountNo) {
        return accountRepository
                .findById(accountNo)
                .orElseThrow(() -> new BusinessException(
                        AccountErrorCode.ACCOUNT_NOT_FOUND,
                        "Account not found: " + AccountNumberMasker.mask(accountNo)));
    }
}
