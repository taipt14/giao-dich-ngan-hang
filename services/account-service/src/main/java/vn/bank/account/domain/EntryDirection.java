package vn.bank.account.domain;

/** Huong cua mot dong so cai, cung quy uoc cho tai khoan khach hang va GL (docs/ledger.md muc 2). */
public enum EntryDirection {
    /** No: tien ra khoi tai khoan, so du giam. */
    DEBIT,

    /** Co: tien vao tai khoan, so du tang. */
    CREDIT
}
