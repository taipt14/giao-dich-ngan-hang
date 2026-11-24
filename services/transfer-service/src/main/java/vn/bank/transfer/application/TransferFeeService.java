package vn.bank.transfer.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.bank.transfer.domain.BankZone;
import vn.bank.transfer.domain.FeeCalculator;
import vn.bank.transfer.domain.TransferType;
import vn.bank.transfer.infrastructure.persistence.FeeScheduleRepository;

/**
 * Phi tinh luc tao lenh (BANK-044), luu vao TRANSFER.FEE va tra trong response: bieu phi FEE_SCHEDULE dang hieu luc
 * trong ngay nghiep vu hom nay ({@link BankZone}).
 */
@Service
@RequiredArgsConstructor
public class TransferFeeService {

    private final FeeScheduleRepository feeScheduleRepository;
    private final Clock clock;

    public BigDecimal calculateFee(TransferType type, BigDecimal amount) {
        // Loc ngay hieu luc trong Java thay vi tham so query: bieu phi it dong, khong phu thuoc cach bind LocalDate
        var schedules = feeScheduleRepository.findByTransferType(type);
        return FeeCalculator.calculate(schedules, type, amount, LocalDate.now(clock.withZone(BankZone.ZONE)));
    }
}
