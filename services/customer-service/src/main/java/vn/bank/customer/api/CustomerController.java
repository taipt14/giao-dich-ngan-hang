package vn.bank.customer.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.bank.common.error.BusinessException;
import vn.bank.customer.application.ChangeCustomerStatusService;
import vn.bank.customer.application.GetCustomerService;
import vn.bank.customer.application.RegisterCustomerService;
import vn.bank.customer.application.UpdateCustomerContactService;
import vn.bank.customer.domain.CustomerErrorCode;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
class CustomerController {

    // Gateway dat header nay tu claim cif cua JWT (BANK-011); user noi bo khong co CIF thi khong co header
    static final String CIF_HEADER = "X-Cif";

    private final RegisterCustomerService registerCustomerService;
    private final GetCustomerService getCustomerService;
    private final UpdateCustomerContactService updateCustomerContactService;
    private final ChangeCustomerStatusService changeCustomerStatusService;
    private final CustomerMapper customerMapper;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresRole(Role.ADMIN)
    CustomerResponse register(@Valid @RequestBody CreateCustomerRequest request) {
        var customer = registerCustomerService.register(customerMapper.toProfile(request));
        return customerMapper.toResponse(customer);
    }

    @GetMapping("/me")
    @RequiresRole(Role.CUSTOMER)
    CustomerResponse getMe(@RequestHeader(name = CIF_HEADER, required = false) String cif) {
        if (cif == null) {
            throw new BusinessException(CustomerErrorCode.ACCESS_DENIED, "Authenticated user has no CIF");
        }
        return customerMapper.toResponse(getCustomerService.getCustomer(cif));
    }

    @GetMapping("/{cif}")
    @RequiresRole(Role.ADMIN)
    CustomerResponse getCustomer(@PathVariable String cif) {
        return customerMapper.toResponse(getCustomerService.getCustomer(cif));
    }

    @PatchMapping("/{cif}")
    @RequiresRole(Role.ADMIN)
    CustomerResponse updateContact(@PathVariable String cif, @Valid @RequestBody UpdateCustomerRequest request) {
        var customer = updateCustomerContactService.updateContact(cif, customerMapper.toContactUpdate(request));
        return customerMapper.toResponse(customer);
    }

    @PostMapping("/{cif}/status")
    @RequiresRole(Role.ADMIN)
    CustomerResponse changeStatus(@PathVariable String cif, @Valid @RequestBody ChangeCustomerStatusRequest request) {
        var customer = changeCustomerStatusService.changeStatus(cif, request.status(), request.reason());
        return customerMapper.toResponse(customer);
    }
}
