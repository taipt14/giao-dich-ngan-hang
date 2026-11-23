package vn.bank.customer.application;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.bank.customer.domain.Customer;
import vn.bank.customer.domain.CustomerStatus;
import vn.bank.customer.domain.CustomerStatusChange;
import vn.bank.customer.infrastructure.messaging.CustomerStatusChangedProducer;
import vn.bank.customer.infrastructure.persistence.CustomerRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChangeCustomerStatusService {

    private final GetCustomerService getCustomerService;
    private final CustomerRepository customerRepository;
    private final CustomerStatusChangedProducer customerStatusChangedProducer;
    private final Clock clock;

    @Transactional
    public Customer changeStatus(String cif, CustomerStatus target, String reason) {
        var customer = getCustomerService.getCustomer(cif);
        var change = customer.changeStatus(target, reason, clock);
        CustomerFlusher.flush(customerRepository, customer);
        publishAfterCommit(change);
        return customer;
    }

    // Muc 8 convention: khong gui Kafka trong transaction DB. Tam gui truc tiep sau commit, BANK-046 thay bang outbox
    private void publishAfterCommit(CustomerStatusChange change) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info(
                        "Customer {} status changed from {} to {}",
                        change.cif(),
                        change.previousStatus(),
                        change.status());
                customerStatusChangedProducer.send(change);
            }
        });
    }
}
