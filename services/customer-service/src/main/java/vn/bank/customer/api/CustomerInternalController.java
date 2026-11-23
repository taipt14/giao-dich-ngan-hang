package vn.bank.customer.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.bank.customer.application.GetCustomerService;

/**
 * API noi bo cho service khac kiem tra trang thai khach hang. Duong dan {@code /internal/**} khong duoc route qua
 * gateway (muc 10 convention) nen khong kiem tra role.
 */
@RestController
@RequestMapping("/internal/customers")
@RequiredArgsConstructor
class CustomerInternalController {

    private final GetCustomerService getCustomerService;
    private final CustomerMapper customerMapper;

    @GetMapping("/{cif}")
    CustomerStatusResponse getStatus(@PathVariable String cif) {
        return customerMapper.toStatusResponse(getCustomerService.getCustomer(cif));
    }
}
