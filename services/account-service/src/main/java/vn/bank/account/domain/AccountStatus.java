package vn.bank.account.domain;

/**
 * Trang thai tai khoan va bang chuyen trang thai hop le: ACTIVE ↔ LOCKED, ACTIVE/LOCKED → CLOSED. CLOSED la trang thai
 * cuoi, khong mo lai.
 */
public enum AccountStatus {
    ACTIVE,
    LOCKED,
    CLOSED;

    public boolean canTransitionTo(AccountStatus target) {
        return switch (this) {
            case ACTIVE -> target == LOCKED || target == CLOSED;
            case LOCKED -> target == ACTIVE || target == CLOSED;
            case CLOSED -> false;
        };
    }
}
