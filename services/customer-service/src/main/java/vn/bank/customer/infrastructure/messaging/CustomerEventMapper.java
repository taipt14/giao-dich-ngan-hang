package vn.bank.customer.infrastructure.messaging;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import vn.bank.common.events.customer.CustomerCreated;
import vn.bank.common.events.customer.CustomerStatusChanged;
import vn.bank.customer.domain.Customer;
import vn.bank.customer.domain.CustomerStatusChange;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface CustomerEventMapper {

    CustomerCreated toCustomerCreated(Customer customer);

    CustomerStatusChanged toCustomerStatusChanged(CustomerStatusChange change);
}
