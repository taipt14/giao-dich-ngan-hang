package vn.bank.transfer.domain;

import java.math.BigDecimal;
import vn.bank.common.error.BusinessException;

/**
 * Thong tin tai khoan doc tu account-service luc tao lenh: chu so huu, trang thai va so du kha dung. {@code cif} null
 * voi tai khoan GL cua ngan hang. Chi dung de kiem tra luc tao lenh; so du va trang thai khi tru tien do account-service
 * kiem tra lai.
 */
public record AccountSnapshot(String accountNo, String cif, String status, BigDecimal availableBalance) {

    static final String ACTIVE = "ACTIVE";

    /** Tai khoan nguon phai thuoc {@code customerCif}, khong thi {@code SOURCE_ACCOUNT_ACCESS_DENIED}. */
    public void requireOwnedBy(String customerCif) {
        // Tai khoan GL khong co CIF nen khong thuoc khach hang nao
        if (cif == null || !cif.equals(customerCif)) {
            throw new BusinessException(
                    TransferErrorCode.SOURCE_ACCOUNT_ACCESS_DENIED,
                    "Source account " + AccountNumberMasker.mask(accountNo)
                            + " does not belong to the authenticated customer");
        }
    }

    /**
     * So du kha dung phai du tru {@code amount} cong {@code fee} (BANK-044), khong thi {@code INSUFFICIENT_BALANCE}.
     * account-service van kiem tra lai khi tru tien vi so du co the doi sau luc tao lenh.
     */
    public void requireAvailableBalanceFor(BigDecimal amount, BigDecimal fee) {
        if (availableBalance.compareTo(amount.add(fee)) < 0) {
            throw new BusinessException(
                    TransferErrorCode.INSUFFICIENT_BALANCE,
                    "Available balance of account " + AccountNumberMasker.mask(accountNo)
                            + " is less than amount plus fee");
        }
    }

    /**
     * Tai khoan dich noi bo phai la tai khoan khach hang ({@code DEST_ACCOUNT_NOT_FOUND} voi tai khoan GL: khach hang
     * khong chuyen thang vao tai khoan noi bo cua ngan hang) va ACTIVE ({@code DEST_ACCOUNT_NOT_ACTIVE}).
     */
    public void requireActiveDestination() {
        if (cif == null) {
            throw new BusinessException(
                    TransferErrorCode.DEST_ACCOUNT_NOT_FOUND,
                    "Destination account not found: " + AccountNumberMasker.mask(accountNo));
        }
        if (!ACTIVE.equals(status)) {
            throw new BusinessException(
                    TransferErrorCode.DEST_ACCOUNT_NOT_ACTIVE,
                    "Destination account " + AccountNumberMasker.mask(accountNo) + " is " + status);
        }
    }
}
