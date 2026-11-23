package vn.bank.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vn.bank.common.error.BusinessException;
import vn.bank.customer.domain.Customer;
import vn.bank.customer.domain.CustomerContactUpdate;
import vn.bank.customer.domain.CustomerErrorCode;
import vn.bank.customer.domain.CustomerProfile;
import vn.bank.customer.infrastructure.persistence.CustomerRepository;

class UpdateCustomerContactServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC);
    private static final String CIF = "C000000007";
    private static final String NEW_PHONE = "0987654321";

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final UpdateCustomerContactService updateCustomerContactService =
            new UpdateCustomerContactService(new GetCustomerService(customerRepository), customerRepository);

    @Test
    void should_updateContactAndFlush_when_customerExists() {
        // given
        givenCustomer();

        // when
        var customer = updateCustomerContactService.updateContact(
                CIF, new CustomerContactUpdate(NEW_PHONE, "new@example.com", "2 Trang Tien, Ha Noi"));

        // then
        assertThat(customer.getPhoneNumber()).isEqualTo(NEW_PHONE);
        assertThat(customer.getEmail()).isEqualTo("new@example.com");
        assertThat(customer.getAddress()).isEqualTo("2 Trang Tien, Ha Noi");
        verify(customerRepository).flush();
    }

    @Test
    void should_return404CustomerNotFound_when_cifUnknown() {
        // given
        when(customerRepository.findById(CIF)).thenReturn(Optional.empty());

        // when
        var thrown = catchThrowable(
                () -> updateCustomerContactService.updateContact(CIF, new CustomerContactUpdate(null, "a@b.vn", null)));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.CUSTOMER_NOT_FOUND);
    }

    @Test
    void should_rejectUpdate_when_phoneNumberUsedByOtherCustomer() {
        // given
        var customer = givenCustomer();
        when(customerRepository.existsByPhoneNumberAndCifNot(NEW_PHONE, CIF)).thenReturn(true);

        // when
        var thrown = catchThrowable(() ->
                updateCustomerContactService.updateContact(CIF, new CustomerContactUpdate(NEW_PHONE, null, null)));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.CUSTOMER_PHONE_DUPLICATED);
        assertThat(customer.getPhoneNumber()).isEqualTo("0912345678");
        verify(customerRepository, never()).flush();
    }

    @Test
    void should_throwConcurrentModificationWithCause_when_optimisticLockFails() {
        // given
        givenCustomer();
        var lockFailure = new ObjectOptimisticLockingFailureException(Customer.class, CIF);
        doThrow(lockFailure).when(customerRepository).flush();

        // when
        var thrown = catchThrowable(
                () -> updateCustomerContactService.updateContact(CIF, new CustomerContactUpdate(null, "a@b.vn", null)));

        // then
        assertThat(thrown).isInstanceOf(BusinessException.class).hasCause(lockFailure);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(CustomerErrorCode.CONCURRENT_MODIFICATION);
        assertThat(CustomerErrorCode.CONCURRENT_MODIFICATION.httpStatus()).isEqualTo(409);
    }

    private Customer givenCustomer() {
        var profile = new CustomerProfile(
                "Nguyen Van A",
                LocalDate.of(1990, 5, 15),
                "001234567889",
                "0912345678",
                "a@example.com",
                "1 Ly Thai To, Ha Noi");
        var customer = Customer.register(CIF, profile, CLOCK);
        when(customerRepository.findById(CIF)).thenReturn(Optional.of(customer));
        return customer;
    }
}
