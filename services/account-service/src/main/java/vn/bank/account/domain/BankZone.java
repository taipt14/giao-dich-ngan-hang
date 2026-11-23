package vn.bank.account.domain;

import java.time.ZoneId;

/** Mui gio xac dinh ngay nghiep vu (muc 4.2 convention). */
public final class BankZone {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private BankZone() {}
}
