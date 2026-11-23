package vn.bank.account.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountType;

public interface AccountRepository extends JpaRepository<Account, String> {

    long countByCifAndAccountType(String cif, AccountType accountType);

    // Dung index IX_ACCOUNT_CIF; so tai khoan tang theo sequence nen sap theo so tai khoan = theo thu tu mo
    List<Account> findByCifOrderByAccountNoAsc(String cif);

    @Query(value = "SELECT SEQ_ACCOUNT_NO.NEXTVAL FROM DUAL", nativeQuery = true)
    long nextAccountNoSequence();
}
