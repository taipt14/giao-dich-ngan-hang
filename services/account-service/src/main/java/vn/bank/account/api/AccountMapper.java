package vn.bank.account.api;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountBalance;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface AccountMapper {

    AccountResponse toResponse(Account account);

    List<AccountResponse> toResponses(List<Account> accounts);

    AccountBalanceResponse toBalanceResponse(AccountBalance balance);
}
