package vn.bank.account.domain;

import java.time.ZoneId;

/** Mui gio xac dinh ngay nghiep vu (muc 4.2 convention). */
public final class BankZone {

    /** Dang chuoi cho annotation can hang so, vi du {@code @Scheduled(zone = ...)}. */
    public static final String ZONE_ID = "Asia/Ho_Chi_Minh";

    public static final ZoneId ZONE = ZoneId.of(ZONE_ID);

    private BankZone() {}
}
