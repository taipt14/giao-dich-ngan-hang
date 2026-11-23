package vn.bank.account.domain;

/** Loai loi doi soat cuoi ngay (BANK-034), khop CHECK {@code CK_RECONCILE_RESULT_ERROR_TYPE}. */
public enum ReconcileErrorType {
    /** BALANCE cua tai khoan khac tong so cai (Co tru No) cua tai khoan do. */
    BALANCE_MISMATCH,

    /** Journal co tong No khac tong Co. */
    JOURNAL_UNBALANCED
}
