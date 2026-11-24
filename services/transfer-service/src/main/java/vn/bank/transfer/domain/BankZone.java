package vn.bank.transfer.domain;

import java.time.ZoneId;

/** Mui gio xac dinh ngay nghiep vu (muc 4.2 convention): han muc ngay tinh lai tu 00:00 gio Viet Nam (BANK-043). */
public final class BankZone {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private BankZone() {}
}
