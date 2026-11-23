package vn.bank.account.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountType;
import vn.bank.account.infrastructure.client.CustomerClient;

/**
 * Mo tai khoan thanh toan: hoi customer-service truoc (ngoai transaction, khong giu connection DB trong luc cho mang),
 * sau do moi ghi DB qua {@link CreateAccountService}.
 */
@Service
@RequiredArgsConstructor
public class OpenAccountService {

    private final CustomerClient customerClient;
    private final CreateAccountService createAccountService;

    public Account open(String cif, AccountType accountType) {
        var holder = customerClient.getAccountHolder(cif);
        return createAccountService.create(holder, accountType);
    }
}
