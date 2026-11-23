package vn.bank.account.application;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.bank.account.domain.Account;
import vn.bank.account.infrastructure.persistence.AccountRepository;

@Service
@RequiredArgsConstructor
public class ListAccountsService {

    private final AccountRepository accountRepository;

    /** Tai khoan cua khach hang theo thu tu mo; khong co tai khoan nao thi tra danh sach rong. */
    @Transactional(readOnly = true)
    public List<Account> listByCif(String cif) {
        return accountRepository.findByCifOrderByAccountNoAsc(cif);
    }
}
