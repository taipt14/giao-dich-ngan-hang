package vn.bank.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.bank.account.domain.AccountStatusHistory;

public interface AccountStatusHistoryRepository extends JpaRepository<AccountStatusHistory, Long> {}
