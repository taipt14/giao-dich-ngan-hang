package vn.bank.account.domain;

public enum AccountType {
    /** Tai khoan thanh toan cua khach hang: co CIF, so du khong am. */
    CURRENT,

    /**
     * Tai khoan noi bo cua ngan hang (CASH_VAULT, FEE_INCOME, NAPAS_SETTLEMENT): tao san bang migration, khong co CIF,
     * duoc phep am, khong mo duoc qua API.
     */
    GL;

    public boolean isCustomerAccount() {
        return this != GL;
    }
}
