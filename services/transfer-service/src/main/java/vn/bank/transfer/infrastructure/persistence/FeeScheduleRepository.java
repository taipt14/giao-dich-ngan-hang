package vn.bank.transfer.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.bank.transfer.domain.FeeSchedule;
import vn.bank.transfer.domain.TransferType;

public interface FeeScheduleRepository extends JpaRepository<FeeSchedule, Long> {

    /** Moi bieu phi (moi ngay hieu luc) cua mot loai giao dich; chon bieu phi nao do FeeCalculator quyet dinh. */
    List<FeeSchedule> findByTransferType(TransferType transferType);
}
