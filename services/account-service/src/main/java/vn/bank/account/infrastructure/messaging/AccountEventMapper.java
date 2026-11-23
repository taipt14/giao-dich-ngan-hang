package vn.bank.account.infrastructure.messaging;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountStatusChange;
import vn.bank.account.domain.BalanceChange;
import vn.bank.common.events.account.AccountOpened;
import vn.bank.common.events.account.AccountStatusChanged;
import vn.bank.common.events.account.BalanceChanged;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface AccountEventMapper {

    @Mapping(target = "openedAt", source = "createdAt")
    AccountOpened toAccountOpened(Account account);

    AccountStatusChanged toAccountStatusChanged(AccountStatusChange change);

    // Viet tay: method MapStruct sinh ra cho 11 field (kem kiem tra null) dai qua 30 dong, Checkstyle quet ca code sinh
    default BalanceChanged toBalanceChanged(BalanceChange change) {
        return new BalanceChanged(
                change.accountNo(),
                change.cif(),
                change.journalId(),
                change.referenceType().name(),
                change.referenceId(),
                change.direction().name(),
                change.amount(),
                change.balanceAfter(),
                change.currency(),
                change.description(),
                change.changedAt());
    }
}
