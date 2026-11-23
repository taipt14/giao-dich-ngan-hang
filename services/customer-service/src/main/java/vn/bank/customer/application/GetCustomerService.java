package vn.bank.customer.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.bank.common.error.BusinessException;
import vn.bank.customer.domain.Customer;
import vn.bank.customer.domain.CustomerErrorCode;
import vn.bank.customer.infrastructure.persistence.CustomerRepository;

@Service
@RequiredArgsConstructor
public class GetCustomerService {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public Customer getCustomer(String cif) {
        return customerRepository
                .findById(cif)
                .orElseThrow(() ->
                        new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND, "Customer not found: " + cif));
    }
}
