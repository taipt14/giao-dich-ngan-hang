package vn.bank.customer.infrastructure.messaging;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import vn.bank.common.events.customer.CustomerCreated;
import vn.bank.customer.domain.Customer;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface CustomerEventMapper {

    CustomerCreated toCustomerCreated(Customer customer);
}
