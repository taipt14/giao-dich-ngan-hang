package vn.bank.account.api;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import vn.bank.account.domain.Account;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface AccountMapper {

    AccountResponse toResponse(Account account);
}
