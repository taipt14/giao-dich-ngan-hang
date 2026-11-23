package vn.bank.account.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.bank.account.application.GetAccountBalanceService;
import vn.bank.account.application.ListAccountsService;
import vn.bank.account.application.OpenAccountService;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.common.error.BusinessException;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
class AccountController {

    // Gateway dat header nay tu claim cif cua JWT (BANK-011); user noi bo khong co CIF thi khong co header
    static final String CIF_HEADER = "X-Cif";

    private final OpenAccountService openAccountService;
    private final ListAccountsService listAccountsService;
    private final GetAccountBalanceService getAccountBalanceService;
    private final AccountMapper accountMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole(Role.ADMIN)
    AccountResponse open(@Valid @RequestBody OpenAccountRequest request) {
        var account = openAccountService.open(request.cif(), request.accountType());
        return accountMapper.toResponse(account);
    }

    @GetMapping
    @RequiresRole(Role.CUSTOMER)
    AccountListResponse listMine(@RequestHeader(name = CIF_HEADER, required = false) String cif) {
        var accounts = listAccountsService.listByCif(requireCif(cif));
        return new AccountListResponse(accountMapper.toResponses(accounts));
    }

    @GetMapping("/{accountNo}/balance")
    @RequiresRole(Role.CUSTOMER)
    AccountBalanceResponse getBalance(
            @RequestHeader(name = CIF_HEADER, required = false) String cif, @PathVariable String accountNo) {
        return accountMapper.toBalanceResponse(getAccountBalanceService.getBalance(requireCif(cif), accountNo));
    }

    private static String requireCif(String cif) {
        if (cif == null) {
            throw new BusinessException(AccountErrorCode.ACCESS_DENIED, "Authenticated user has no CIF");
        }
        return cif;
    }
}
