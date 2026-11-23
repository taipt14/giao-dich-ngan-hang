package vn.bank.transfer.application;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import vn.bank.transfer.domain.TransferType;

/**
 * Phi du kien tinh luc tao lenh, luu vao TRANSFER.FEE va tra trong response. Chua co bieu phi: moi lenh tam tinh phi
 * 0 (dung voi chuyen noi bo, von mien phi) cho den khi BANK-044 thay bang bieu phi FEE_SCHEDULE.
 */
@Component
public class TransferFeeEstimator {

    public BigDecimal estimate(TransferType type, BigDecimal amount) {
        return BigDecimal.ZERO;
    }
}
