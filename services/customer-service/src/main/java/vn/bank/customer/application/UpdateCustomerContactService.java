package vn.bank.customer.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.bank.common.error.BusinessException;
import vn.bank.customer.domain.Customer;
import vn.bank.customer.domain.CustomerContactUpdate;
import vn.bank.customer.domain.CustomerErrorCode;
import vn.bank.customer.infrastructure.persistence.CustomerRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateCustomerContactService {

    private final GetCustomerService getCustomerService;
    private final CustomerRepository customerRepository;

    @Transactional
    public Customer updateContact(String cif, CustomerContactUpdate update) {
        var customer = getCustomerService.getCustomer(cif);
        ensurePhoneNotUsedByOthers(cif, update.phoneNumber());
        customer.updateContact(update);
        CustomerFlusher.flush(customerRepository, customer);
        log.info("Customer {} contact updated", cif);
        return customer;
    }

    // Kiem tra truoc de tra dung ma loi; 2 request dong thoi van bi UK_CUSTOMER_PHONE_NUMBER trong DB chan
    private void ensurePhoneNotUsedByOthers(String cif, String phoneNumber) {
        if (phoneNumber != null && customerRepository.existsByPhoneNumberAndCifNot(phoneNumber, cif)) {
            throw new BusinessException(CustomerErrorCode.CUSTOMER_PHONE_DUPLICATED, "Phone number already registered");
        }
    }
}
