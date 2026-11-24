package vn.bank.transfer.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.bank.transfer.domain.CustomerType;
import vn.bank.transfer.domain.TransferLimitConfig;

public interface TransferLimitConfigRepository extends JpaRepository<TransferLimitConfig, CustomerType> {}
