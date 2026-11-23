package vn.bank.transfer.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.bank.transfer.domain.Transfer;

public interface TransferRepository extends JpaRepository<Transfer, String> {

    /** Lenh da tao bang Idempotency-Key cua khach hang (duy nhat theo UK_TRANSFER_CIF_IDEM_KEY). */
    Optional<Transfer> findByCifAndIdempotencyKey(String cif, String idempotencyKey);
}
