package vn.bank.account.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountStatus;
import vn.bank.account.domain.AccountType;

public interface AccountRepository extends JpaRepository<Account, String> {

    long countByCifAndAccountType(String cif, AccountType accountType);

    // Dung index IX_ACCOUNT_CIF; so tai khoan tang theo sequence nen sap theo so tai khoan = theo thu tu mo
    List<Account> findByCifOrderByAccountNoAsc(String cif);

    // Dung index IX_ACCOUNT_CIF: khoa toan bo tai khoan ACTIVE khi khach hang bi khoa
    List<Account> findByCifAndStatusOrderByAccountNoAsc(String cif, AccountStatus status);

    /**
     * Khoa ghi ({@code SELECT ... FOR UPDATE}) cac tai khoan cua mot posting den het transaction, de doc so du va ghi
     * BALANCE_AFTER khong bi posting khac chen vao. Timeout cho khoa va chong deadlock thuoc BANK-032.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNo IN :accountNos ORDER BY a.accountNo")
    List<Account> findAllForUpdate(@Param("accountNos") Collection<String> accountNos);

    @Query(value = "SELECT SEQ_ACCOUNT_NO.NEXTVAL FROM DUAL", nativeQuery = true)
    long nextAccountNoSequence();
}
