package vn.bank.customer.api;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import vn.bank.customer.domain.Customer;
import vn.bank.customer.domain.CustomerContactUpdate;
import vn.bank.customer.domain.CustomerProfile;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface CustomerMapper {

    CustomerProfile toProfile(CreateCustomerRequest request);

    CustomerContactUpdate toContactUpdate(UpdateCustomerRequest request);

    // Khong bao gio tra so CCCD day du ra API
    @Mapping(target = "idNumber", source = "maskedIdNumber")
    CustomerResponse toResponse(Customer customer);

    CustomerStatusResponse toStatusResponse(Customer customer);
}
