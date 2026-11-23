package vn.bank.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import vn.bank.common.error.BusinessException;

class CustomerTest {

    // 2026-11-21 06:00 gio Viet Nam: ngay nghiep vu la 2026-11-21
    private static final Instant NOW = Instant.parse("2026-11-20T23:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CIF = "C000000001";

    @Test
    void should_registerActiveCustomer_when_customerIsAdult() {
        // given
        var profile = profileBornOn(LocalDate.of(1990, 5, 15));

        // when
        var customer = Customer.register(CIF, profile, CLOCK);

        // then
        assertThat(customer.getCif()).isEqualTo(CIF);
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(customer.getCreatedAt()).isEqualTo(NOW);
        assertThat(customer.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(customer.getIdNumber()).isEqualTo("001234567889");
    }

    @Test
    void should_registerCustomer_when_customerTurns18OnBusinessDate() {
        // given: tron 18 tuoi dung ngay 2026-11-21 theo gio Viet Nam (ngay UTC van la 2026-11-20)
        var profile = profileBornOn(LocalDate.of(2008, 11, 21));

        // when
        var customer = Customer.register(CIF, profile, CLOCK);

        // then
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void should_rejectRegistration_when_customerUnder18() {
        // given: con 1 ngay nua moi du 18 tuoi
        var profile = profileBornOn(LocalDate.of(2008, 11, 22));

        // when
        var thrown = catchThrowable(() -> Customer.register(CIF, profile, CLOCK));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.CUSTOMER_UNDERAGE);
    }

    @Test
    void should_returnMaskedIdNumber_when_askedForMaskedIdNumber() {
        // given
        var customer = Customer.register(CIF, profileBornOn(LocalDate.of(1990, 5, 15)), CLOCK);

        // when
        var masked = customer.getMaskedIdNumber();

        // then
        assertThat(masked).isEqualTo("0012******89");
    }

    private static CustomerProfile profileBornOn(LocalDate dateOfBirth) {
        return new CustomerProfile(
                "Nguyen Van A", dateOfBirth, "001234567889", "0912345678", "a@example.com", "1 Ly Thai To, Ha Noi");
    }
}
