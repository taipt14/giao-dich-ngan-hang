package vn.bank.account.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
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

    /** Thoi gian toi da cho khoa mot tai khoan (ms). Oracle sinh {@code FOR UPDATE WAIT 5}. */
    String LOCK_TIMEOUT_MILLIS = "5000";

    /**
     * Khoa ghi ({@code SELECT ... FOR UPDATE WAIT}) mot tai khoan den het transaction, de doc so du va ghi BALANCE_AFTER
     * khong bi posting khac chen vao. Cho qua {@link #LOCK_TIMEOUT_MILLIS} → {@code PessimisticLockingFailureException}.
     * Moi lan chi khoa mot tai khoan de nguoi goi quyet dinh thu tu khoa (chong deadlock): mot cau {@code IN (...)} khong
     * dam bao Oracle khoa cac dong theo thu tu {@code ORDER BY}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = LOCK_TIMEOUT_MILLIS))
    @Query("SELECT a FROM Account a WHERE a.accountNo = :accountNo")
    Optional<Account> findForUpdate(@Param("accountNo") String accountNo);

    @Query(value = "SELECT SEQ_ACCOUNT_NO.NEXTVAL FROM DUAL", nativeQuery = true)
    long nextAccountNoSequence();
}
