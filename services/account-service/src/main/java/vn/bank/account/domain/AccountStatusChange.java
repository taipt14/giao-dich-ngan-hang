package vn.bank.account.domain;

import java.time.Instant;

/** Ket qua mot lan doi trang thai tai khoan: ghi {@code ACCOUNT_STATUS_HISTORY} va phat event AccountStatusChanged. */
public record AccountStatusChange(
        String accountNo,
        String cif,
        AccountStatus previousStatus,
        AccountStatus status,
        String reason,
        Instant changedAt) {

    public String maskedAccountNo() {
        return AccountNumberMasker.mask(accountNo);
    }
}
