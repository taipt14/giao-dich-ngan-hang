package vn.bank.transfer.domain;

/** Loai lenh chuyen khoan. Lenh noi bo khong di qua NAPAS nen khong co PENDING_NAPAS va REVERSED. */
public enum TransferType {
    INTERNAL,
    INTERBANK;

    public boolean supports(TransferStatus status) {
        return switch (this) {
            case INTERNAL -> status != TransferStatus.PENDING_NAPAS && status != TransferStatus.REVERSED;
            case INTERBANK -> true;
        };
    }
}
