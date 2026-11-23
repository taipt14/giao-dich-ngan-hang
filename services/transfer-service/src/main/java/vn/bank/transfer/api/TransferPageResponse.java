package vn.bank.transfer.api;

import java.util.List;

/** Phan trang theo offset (muc 10 convention): {@code page} tinh tu 0, {@code totalElements} tong so lenh khop loc. */
public record TransferPageResponse(List<TransferSummaryResponse> items, int page, int size, long totalElements) {}
