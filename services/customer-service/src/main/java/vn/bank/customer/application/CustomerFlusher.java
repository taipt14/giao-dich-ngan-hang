package vn.bank.customer.application;

import org.springframework.dao.OptimisticLockingFailureException;
import vn.bank.common.error.BusinessException;
import vn.bank.customer.domain.Customer;
import vn.bank.customer.domain.CustomerErrorCode;
import vn.bank.customer.infrastructure.persistence.CustomerRepository;

/**
 * Flush thay doi cua khach hang ngay trong method nghiep vu: xung dot optimistic lock ({@code VERSION}) lo ra o day
 * thay vi luc commit (sau khi method da tra ve), nen chuyen duoc thanh 409 {@code CONCURRENT_MODIFICATION}.
 */
final class CustomerFlusher {

    private CustomerFlusher() {}

    static void flush(CustomerRepository customerRepository, Customer customer) {
        try {
            customerRepository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new BusinessException(
                    CustomerErrorCode.CONCURRENT_MODIFICATION,
                    "Customer " + customer.getCif() + " was modified by another request, please retry",
                    e);
        }
    }
}
