package vn.bank.account.domain;

/** Trang thai mot loi doi soat theo lan chay moi nhat cua ngay nghiep vu. */
public enum ReconcileStatus {
    /** Lan chay moi nhat van thay loi. */
    OPEN,

    /** Lan truoc thay loi, lan chay moi nhat khong con thay. */
    RESOLVED
}
