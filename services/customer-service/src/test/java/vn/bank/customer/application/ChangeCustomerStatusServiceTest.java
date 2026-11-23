package vn.bank.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.bank.common.error.BusinessException;
import vn.bank.customer.domain.Customer;
import vn.bank.customer.domain.CustomerErrorCode;
import vn.bank.customer.domain.CustomerProfile;
import vn.bank.customer.domain.CustomerStatus;
import vn.bank.customer.domain.CustomerStatusChange;
import vn.bank.customer.infrastructure.messaging.CustomerStatusChangedProducer;
import vn.bank.customer.infrastructure.persistence.CustomerRepository;

class ChangeCustomerStatusServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CIF = "C000000007";
    private static final String REASON = "Nghi ngo gian lan";

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final CustomerStatusChangedProducer producer = mock(CustomerStatusChangedProducer.class);
    private final ChangeCustomerStatusService changeCustomerStatusService = new ChangeCustomerStatusService(
            new GetCustomerService(customerRepository), customerRepository, producer, CLOCK);

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
    void should_writeCustomerStatusChangedToOutboxInsideTransaction_when_statusChanged() {
        // given
        givenActiveCustomer();

        // when
        var customer = changeCustomerStatusService.changeStatus(CIF, CustomerStatus.LOCKED, REASON);

        // then: ghi outbox ngay trong transaction, khong doi afterCommit (rollback: CustomerOutboxIT)
        assertThat(customer.getStatus()).isEqualTo(CustomerStatus.LOCKED);
        verify(customerRepository).flush();
        verify(producer).send(new CustomerStatusChange(CIF, CustomerStatus.ACTIVE, CustomerStatus.LOCKED, REASON, NOW));
    }

    @Test
    void should_throwConcurrentModificationAndNotPublish_when_optimisticLockFails() {
        // given
        givenActiveCustomer();
        var lockFailure = new ObjectOptimisticLockingFailureException(Customer.class, CIF);
        doThrow(lockFailure).when(customerRepository).flush();

        // when
        var thrown = catchThrowable(() -> changeCustomerStatusService.changeStatus(CIF, CustomerStatus.LOCKED, REASON));

        // then
        assertThat(thrown).isInstanceOf(BusinessException.class).hasCause(lockFailure);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(CustomerErrorCode.CONCURRENT_MODIFICATION);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verify(producer, never()).send(any());
    }

    private void givenActiveCustomer() {
        var profile = new CustomerProfile(
                "Nguyen Van A",
                LocalDate.of(1990, 5, 15),
                "001234567889",
                "0912345678",
                "a@example.com",
                "1 Ly Thai To, Ha Noi");
        when(customerRepository.findById(CIF)).thenReturn(Optional.of(Customer.register(CIF, profile, CLOCK)));
    }
}
