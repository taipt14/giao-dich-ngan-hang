package vn.bank.account.domain;

/**
 * Khach hang chu tai khoan theo customer-service tai thoi diem mo tai khoan. {@code customerStatus} giu dang chuoi:
 * customer-service them trang thai moi thi account-service van doc duoc va coi la khong ACTIVE.
 */
public record AccountHolder(String cif, String customerStatus) {

    static final String ACTIVE_STATUS = "ACTIVE";

    public boolean isActive() {
        return ACTIVE_STATUS.equals(customerStatus);
    }
}
