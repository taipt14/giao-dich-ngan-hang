package vn.bank.transfer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.bank.transfer.domain.Transfer;

public interface TransferRepository extends JpaRepository<Transfer, String> {}
