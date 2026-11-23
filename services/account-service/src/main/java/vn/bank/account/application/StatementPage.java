package vn.bank.account.application;

import java.util.List;
import vn.bank.account.domain.StatementLine;

/** Mot trang sao ke, moi nhat truoc. {@code nextCursor} null khi khong con dong nao. */
public record StatementPage(List<StatementLine> items, int size, Long nextCursor) {

    public StatementPage {
        items = List.copyOf(items);
    }
}
