package vn.bank.account.api;

import java.util.List;

/**
 * Boc danh sach trong {@code items} (muc 10 convention): moi referenceId toi da mot journal moi loai nen chua phan
 * trang.
 */
public record PostingListResponse(List<PostingResponse> items) {}
