package vn.bank.common.error;

/** Chi tiet mot loi, vi du mot truong khong hop le. */
public record ErrorDetail(String field, String message) {}
