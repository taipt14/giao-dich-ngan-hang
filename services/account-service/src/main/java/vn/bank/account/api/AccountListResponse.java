package vn.bank.account.api;

import java.util.List;

/**
 * Boc danh sach trong {@code items} (muc 10 convention): moi khach hang toi da 3 tai khoan nen chua phan trang, sau nay
 * them {@code page}/{@code size} van tuong thich nguoc.
 */
public record AccountListResponse(List<AccountResponse> items) {}
