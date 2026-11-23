package vn.bank.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import vn.bank.common.error.BusinessException;

@Entity
@Table(name = "CUSTOMER")
@Getter
public class Customer {

    static final int ADULT_AGE = 18;

    @Id
    @Column(name = "CIF")
    private String cif;

    @Column(name = "FULL_NAME")
    private String fullName;

    @Column(name = "DATE_OF_BIRTH")
    private LocalDate dateOfBirth;

    @Column(name = "ID_NUMBER")
    private String idNumber;

    @Column(name = "PHONE_NUMBER")
    private String phoneNumber;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "ADDRESS")
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS")
    private CustomerStatus status;

    @Column(name = "CREATED_AT")
    private Instant createdAt;

    // Kieu wrapper: CIF la khoa gan san, version null giup Spring Data nhan ra entity moi (persist, khong merge)
    @Version
    @Column(name = "VERSION")
    private Long version;

    protected Customer() {}

    private Customer(String cif, CustomerProfile profile, Instant createdAt) {
        this.cif = cif;
        this.fullName = profile.fullName();
        this.dateOfBirth = profile.dateOfBirth();
        this.idNumber = profile.idNumber();
        this.phoneNumber = profile.phoneNumber();
        this.email = profile.email();
        this.address = profile.address();
        this.status = CustomerStatus.ACTIVE;
        this.createdAt = createdAt;
    }

    /** Dang ky khach hang moi o trang thai ACTIVE. Khach hang chua du 18 tuoi theo ngay nghiep vu bi tu choi. */
    public static Customer register(String cif, CustomerProfile profile, Clock clock) {
        var now = clock.instant();
        var today = LocalDate.ofInstant(now, BankZone.ZONE);
        if (!isAdult(profile.dateOfBirth(), today)) {
            throw new BusinessException(
                    CustomerErrorCode.CUSTOMER_UNDERAGE, "Customer must be at least " + ADULT_AGE + " years old");
        }
        return new Customer(cif, profile, now);
    }

    public String getMaskedIdNumber() {
        return IdNumberMasker.mask(idNumber);
    }

    private static boolean isAdult(LocalDate dateOfBirth, LocalDate today) {
        return !dateOfBirth.plusYears(ADULT_AGE).isAfter(today);
    }
}
