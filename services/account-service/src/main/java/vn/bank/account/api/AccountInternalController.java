package vn.bank.account.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.bank.account.application.GetAccountService;

/**
 * API noi bo tra cuu tai khoan cho service khac (transfer-service, BANK-041), khong route qua gateway (muc 10
 * convention). Tai khoan khong ton tai tra 404 {@code ACCOUNT_NOT_FOUND}.
 */
@RestController
@RequestMapping("/internal/accounts")
@RequiredArgsConstructor
class AccountInternalController {

    private final GetAccountService getAccountService;
    private final AccountMapper accountMapper;

    @GetMapping("/{accountNo}")
    AccountResponse get(@PathVariable String accountNo) {
        return accountMapper.toResponse(getAccountService.getAccount(accountNo));
    }
}
