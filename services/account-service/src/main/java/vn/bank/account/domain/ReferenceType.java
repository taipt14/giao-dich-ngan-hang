package vn.bank.account.domain;

/** Loai nghiep vu goc cua mot journal (CK_JOURNAL_REFERENCE_TYPE, docs/ledger.md muc 4). */
public enum ReferenceType {
    DEPOSIT,
    TRANSFER,
    FEE,
    REVERSAL
}
