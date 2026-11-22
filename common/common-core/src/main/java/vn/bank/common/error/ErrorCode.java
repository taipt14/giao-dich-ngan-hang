package vn.bank.common.error;

/**
 * Ma loi cua mot service. Moi service tu dinh nghia enum rieng implement interface nay.
 * Khong phu thuoc Spring de tang domain dung duoc.
 */
public interface ErrorCode {

    String code();

    int httpStatus();
}
