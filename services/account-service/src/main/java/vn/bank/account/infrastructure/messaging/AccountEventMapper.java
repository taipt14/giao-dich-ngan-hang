package vn.bank.account.infrastructure.messaging;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountStatusChange;
import vn.bank.common.events.account.AccountOpened;
import vn.bank.common.events.account.AccountStatusChanged;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface AccountEventMapper {

    @Mapping(target = "openedAt", source = "createdAt")
    AccountOpened toAccountOpened(Account account);

    AccountStatusChanged toAccountStatusChanged(AccountStatusChange change);
}
