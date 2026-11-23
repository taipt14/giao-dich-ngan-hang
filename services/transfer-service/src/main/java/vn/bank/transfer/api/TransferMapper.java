package vn.bank.transfer.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import vn.bank.transfer.domain.Transfer;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface TransferMapper {

    int MONEY_SCALE = 2;
    String MONEY = "money";

    @Mapping(target = "amount", qualifiedByName = MONEY)
    @Mapping(target = "fee", qualifiedByName = MONEY)
    TransferResponse toResponse(Transfer transfer);

    // Oracle tra NUMBER(19,2) bo so 0 cuoi: dua ve scale 2 de lenh moi va lenh tra lai khi gui lap cung mot JSON
    @Named(MONEY)
    default BigDecimal toMoney(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
