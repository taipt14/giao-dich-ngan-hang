package vn.bank.transfer.domain;

import java.util.Set;

/**
 * Trang thai lenh chuyen khoan va bang chuyen trang thai hop le (so do: docs/transfer-state.md). SUCCESS, FAILED,
 * REVERSED la trang thai cuoi, khong chuyen tiep duoc nua. Khong cho giu nguyen trang thai (tu chuyen sang chinh no).
 */
public enum TransferStatus {
    INITIATED,
    OTP_PENDING,
    PROCESSING,
    PENDING_NAPAS,
    SUCCESS,
    FAILED,
    REVERSED,
    UNKNOWN;

    public boolean canTransitionTo(TransferStatus target) {
        return allowedTargets().contains(target);
    }

    public boolean isFinal() {
        return allowedTargets().isEmpty();
    }

    /**
     * Lenh da giu cho han muc ngay luc xac thuc OTP va chua duoc hoan: so tien tinh vao tong da chuyen trong ngay
     * (BANK-043). PENDING_NAPAS va UNKNOWN van giu vi tien co the da bi tru.
     */
    public boolean holdsDailyLimit() {
        return this == PROCESSING || this == PENDING_NAPAS || this == UNKNOWN || this == SUCCESS;
    }

    /** Lenh khong thanh: han muc ngay da giu (neu co) duoc hoan lai (BANK-043). */
    public boolean releasesDailyLimit() {
        return this == FAILED || this == REVERSED;
    }

    private Set<TransferStatus> allowedTargets() {
        return switch (this) {
            case INITIATED -> Set.of(OTP_PENDING);
            // FAILED: sai OTP qua so lan cho phep (BANK-042), OTP dung nhung vuot han muc ngay (BANK-043)
            case OTP_PENDING -> Set.of(PROCESSING, FAILED);
            // PENDING_NAPAS: lien ngan hang da tru tien (BANK-052); UNKNOWN: loi ky thuat het retry (BANK-045)
            case PROCESSING -> Set.of(SUCCESS, FAILED, PENDING_NAPAS, UNKNOWN);
            // REVERSED: NAPAS tu choi, da hoan tien (BANK-053); UNKNOWN: tra soat het so lan (BANK-054)
            case PENDING_NAPAS -> Set.of(SUCCESS, REVERSED, UNKNOWN);
            // Ket qua tra soat / ADMIN xu ly tay: FAILED khi chac chan tien chua bi tru (BANK-045, BANK-054)
            case UNKNOWN -> Set.of(SUCCESS, FAILED, REVERSED);
            case SUCCESS, FAILED, REVERSED -> Set.of();
        };
    }
}
