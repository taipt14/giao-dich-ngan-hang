package vn.bank.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountType;

public interface AccountRepository extends JpaRepository<Account, String> {

    long countByCifAndAccountType(String cif, AccountType accountType);

    @Query(value = "SELECT SEQ_ACCOUNT_NO.NEXTVAL FROM DUAL", nativeQuery = true)
    long nextAccountNoSequence();
}
