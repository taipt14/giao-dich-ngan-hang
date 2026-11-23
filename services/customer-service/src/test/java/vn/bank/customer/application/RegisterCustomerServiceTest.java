package vn.bank.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.bank.common.error.BusinessException;
import vn.bank.customer.domain.Customer;
import vn.bank.customer.domain.CustomerErrorCode;
import vn.bank.customer.domain.CustomerProfile;
import vn.bank.customer.infrastructure.messaging.CustomerCreatedProducer;
import vn.bank.customer.infrastructure.persistence.CustomerRepository;

class RegisterCustomerServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC);
    private static final CustomerProfile PROFILE = new CustomerProfile(
            "Nguyen Van A",
            LocalDate.of(1990, 5, 15),
            "001234567889",
            "0912345678",
            "a@example.com",
            "1 Ly Thai To, Ha Noi");

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final CustomerCreatedProducer customerCreatedProducer = mock(CustomerCreatedProducer.class);
    private final RegisterCustomerService registerCustomerService =
            new RegisterCustomerService(customerRepository, customerCreatedProducer, CLOCK);

    // Gia lap transaction dang mo de service dang ky duoc callback afterCommit
    @BeforeEach
    void startTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSynchronization() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void should_saveCustomerWithCifFromSequence_when_profileNotRegistered() {
        // given
        givenNewCustomerSaved();

        // when
        var customer = registerCustomerService.register(PROFILE);

        // then
        assertThat(customer.getCif()).isEqualTo("C000000007");
        verify(customerRepository).save(customer);
    }

    @Test
    void should_publishCustomerCreatedOnlyAfterCommit_when_customerRegistered() {
        // given
        givenNewCustomerSaved();

        // when
        var customer = registerCustomerService.register(PROFILE);

        // then
        verify(customerCreatedProducer, never()).send(any());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(customerCreatedProducer).send(customer);
    }

    @Test
    void should_notPublishEvent_when_transactionRolledBack() {
        // given
        givenNewCustomerSaved();

        // when
        registerCustomerService.register(PROFILE);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        // then
        verify(customerCreatedProducer, never()).send(any());
    }

    @Test
    void should_rejectRegistration_when_idNumberDuplicated() {
        // given
        when(customerRepository.existsByIdNumber(PROFILE.idNumber())).thenReturn(true);

        // when
        var thrown = catchThrowable(() -> registerCustomerService.register(PROFILE));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .hasMessageNotContaining(PROFILE.idNumber())
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.CUSTOMER_ID_NUMBER_DUPLICATED);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void should_rejectRegistration_when_phoneNumberDuplicated() {
        // given
        when(customerRepository.existsByPhoneNumber(PROFILE.phoneNumber())).thenReturn(true);

        // when
        var thrown = catchThrowable(() -> registerCustomerService.register(PROFILE));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CustomerErrorCode.CUSTOMER_PHONE_DUPLICATED);
        verify(customerRepository, never()).save(any());
    }

    private void givenNewCustomerSaved() {
        when(customerRepository.nextCifSequence()).thenReturn(7L);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
