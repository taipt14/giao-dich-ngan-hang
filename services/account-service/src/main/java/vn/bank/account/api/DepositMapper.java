package vn.bank.account.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import vn.bank.account.application.DepositResult;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface DepositMapper {

    int MONEY_SCALE = 2;
    String MONEY = "money";

    @Mapping(target = "journalId", source = "journal.id")
    @Mapping(target = "idempotencyKey", source = "journal.referenceId")
    @Mapping(target = "accountNo", source = "creditEntry.accountNo")
    @Mapping(target = "amount", source = "creditEntry.amount", qualifiedByName = MONEY)
    @Mapping(target = "balanceAfter", source = "creditEntry.balanceAfter", qualifiedByName = MONEY)
    @Mapping(target = "description", source = "journal.description")
    @Mapping(target = "createdAt", source = "journal.createdAt")
    DepositResponse toResponse(DepositResult result);

    // Oracle tra NUMBER(19,2) bo so 0 cuoi: dua ve scale 2 de lan nap moi va lan nap lap lai tra cung mot JSON
    @Named(MONEY)
    default BigDecimal toMoney(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
