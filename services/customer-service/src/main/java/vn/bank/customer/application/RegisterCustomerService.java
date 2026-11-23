package vn.bank.customer.application;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.bank.common.error.BusinessException;
import vn.bank.customer.domain.Cif;
import vn.bank.customer.domain.Customer;
import vn.bank.customer.domain.CustomerErrorCode;
import vn.bank.customer.domain.CustomerProfile;
import vn.bank.customer.domain.IdNumberMasker;
import vn.bank.customer.infrastructure.messaging.CustomerCreatedProducer;
import vn.bank.customer.infrastructure.persistence.CustomerRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterCustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerCreatedProducer customerCreatedProducer;
    private final Clock clock;

    @Transactional
    public Customer register(CustomerProfile profile) {
        ensureNotRegistered(profile);
        var cif = Cif.fromSequence(customerRepository.nextCifSequence());
        var customer = customerRepository.save(Customer.register(cif, profile, clock));
        // Outbox (BANK-046): ghi event cung transaction, rollback thi khong co event nao duoc gui
        customerCreatedProducer.send(customer);
        logAfterCommit(customer);
        return customer;
    }

    // Kiem tra truoc de tra dung ma loi; 2 request dong thoi van bi rang buoc UK_CUSTOMER_* trong DB chan
    private void ensureNotRegistered(CustomerProfile profile) {
        if (customerRepository.existsByIdNumber(profile.idNumber())) {
            throw new BusinessException(
                    CustomerErrorCode.CUSTOMER_ID_NUMBER_DUPLICATED,
                    "Id number already registered: " + IdNumberMasker.mask(profile.idNumber()));
        }
        if (customerRepository.existsByPhoneNumber(profile.phoneNumber())) {
            throw new BusinessException(CustomerErrorCode.CUSTOMER_PHONE_DUPLICATED, "Phone number already registered");
        }
    }

    // Moc nghiep vu chi log khi da commit
    private void logAfterCommit(Customer customer) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Customer {} registered, id number {}", customer.getCif(), customer.getMaskedIdNumber());
            }
        });
    }
}
