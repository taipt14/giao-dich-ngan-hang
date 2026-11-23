package vn.bank.account.domain;

import java.math.BigDecimal;

/**
 * So du cua tai khoan tai mot thoi diem, chi de hien thi (co the lay tu cache). Khong dung gia tri nay de quyet dinh
 * tru tien: kiem tra so du khi tru tien phai doc tu DB co khoa.
 */
public record AccountBalance(
        String accountNo, String cif, String currency, BigDecimal balance, BigDecimal availableBalance) {

    /** Tai khoan GL khong co CIF nen khong thuoc khach hang nao. */
    public boolean isOwnedBy(String customerCif) {
        return cif != null && cif.equals(customerCif);
    }
}
