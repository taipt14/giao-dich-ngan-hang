package vn.bank.transfer.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import vn.bank.transfer.application.TransferPage;
import vn.bank.transfer.domain.AccountNumberMasker;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferStatusHistory;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface TransferMapper {

    int MONEY_SCALE = 2;
    String MONEY = "money";
    String MASKED_ACCOUNT = "maskedAccount";

    @Mapping(target = "amount", qualifiedByName = MONEY)
    @Mapping(target = "fee", qualifiedByName = MONEY)
    TransferResponse toResponse(Transfer transfer);

    @Mapping(target = "amount", qualifiedByName = MONEY)
    @Mapping(target = "fee", qualifiedByName = MONEY)
    @Mapping(target = "destAccount", qualifiedByName = MASKED_ACCOUNT)
    TransferSummaryResponse toSummary(Transfer transfer);

    TransferPageResponse toPageResponse(TransferPage page);

    List<TransferStatusHistoryResponse> toHistoryResponses(List<TransferStatusHistory> statusHistory);

    // Viet tay vi ban MapStruct sinh ra (13 field) vuot gioi han 30 dong cua Checkstyle; constructor record van bat
    // buoc truyen du moi field nen them field ma quen map van loi luc build
    default TransferDetailResponse toDetail(Transfer transfer) {
        return new TransferDetailResponse(
                transfer.getTransferId(),
                transfer.getType(),
                transfer.getStatus(),
                transfer.getFailureCode(),
                transfer.getSourceAccount(),
                transfer.getDestAccount(),
                transfer.getDestBankCode(),
                toMoney(transfer.getAmount()),
                toMoney(transfer.getFee()),
                transfer.getDescription(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt(),
                toHistoryResponses(transfer.getStatusHistory()));
    }

    // Oracle tra NUMBER(19,2) bo so 0 cuoi: dua ve scale 2 de lenh moi va lenh tra lai khi gui lap cung mot JSON
    @Named(MONEY)
    default BigDecimal toMoney(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    // Danh sach lenh chi hien mot phan so tai khoan dich (BANK-047)
    @Named(MASKED_ACCOUNT)
    default String toMaskedAccount(String accountNo) {
        return AccountNumberMasker.mask(accountNo);
    }
}
