package vn.bank.account.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.bank.account.application.ChangeAccountStatusService;
import vn.bank.account.domain.AccountStatus;

/** ADMIN khoa, mo khoa, dong tai khoan; moi lenh bat buoc co ly do. */
@RestController
@RequestMapping("/api/v1/accounts/{accountNo}")
@RequiredArgsConstructor
class AccountStatusController {

    private final ChangeAccountStatusService changeAccountStatusService;
    private final AccountMapper accountMapper;

    @PostMapping("/lock")
    @RequiresRole(Role.ADMIN)
    AccountResponse lock(@PathVariable String accountNo, @Valid @RequestBody ChangeAccountStatusRequest request) {
        return changeStatus(accountNo, AccountStatus.LOCKED, request);
    }

    @PostMapping("/unlock")
    @RequiresRole(Role.ADMIN)
    AccountResponse unlock(@PathVariable String accountNo, @Valid @RequestBody ChangeAccountStatusRequest request) {
        return changeStatus(accountNo, AccountStatus.ACTIVE, request);
    }

    @PostMapping("/close")
    @RequiresRole(Role.ADMIN)
    AccountResponse close(@PathVariable String accountNo, @Valid @RequestBody ChangeAccountStatusRequest request) {
        return changeStatus(accountNo, AccountStatus.CLOSED, request);
    }

    private AccountResponse changeStatus(String accountNo, AccountStatus target, ChangeAccountStatusRequest request) {
        var account = changeAccountStatusService.changeStatus(accountNo, target, request.reason());
        return accountMapper.toResponse(account);
    }
}
