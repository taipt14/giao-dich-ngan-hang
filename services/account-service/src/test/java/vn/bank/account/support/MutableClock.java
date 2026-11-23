package vn.bank.account.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Clock co dinh doi duoc thoi diem: integration test ghi but toan o tung thoi diem mong muon (CREATED_AT lay tu Clock)
 * ma khong can tao lai Spring context. Chi dung khi cac test trong context chay tuan tu.
 */
public final class MutableClock extends Clock {

    private volatile Instant instant;

    public MutableClock(Instant instant) {
        this.instant = instant;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
