package vn.bank.transfer.application;

import java.util.List;
import vn.bank.transfer.domain.Transfer;

/** Mot trang lenh chuyen khoan, moi nhat truoc. {@code totalElements}: tong so lenh khop bo loc tren moi trang. */
public record TransferPage(List<Transfer> items, int page, int size, long totalElements) {

    public TransferPage {
        items = List.copyOf(items);
    }
}
