package vn.bank.account.api;

import java.util.List;

/** Phan trang keyset (muc 10 convention): goi lai voi {@code cursor = nextCursor} de lay trang sau, het thi null. */
public record StatementResponse(List<StatementLineResponse> items, int size, Long nextCursor) {}
