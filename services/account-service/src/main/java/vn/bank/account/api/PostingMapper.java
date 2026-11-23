package vn.bank.account.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import vn.bank.account.application.PostingCommand;
import vn.bank.account.application.PostingResult;
import vn.bank.account.domain.LedgerEntry;
import vn.bank.account.domain.PostingLine;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface PostingMapper {

    int MONEY_SCALE = 2;
    String MONEY = "money";

    PostingCommand toCommand(CreatePostingRequest request);

    PostingLine toLine(PostingLineRequest request);

    @Mapping(target = "journalId", source = "journal.id")
    @Mapping(target = "referenceType", source = "journal.referenceType")
    @Mapping(target = "referenceId", source = "journal.referenceId")
    @Mapping(target = "description", source = "journal.description")
    @Mapping(target = "createdAt", source = "journal.createdAt")
    PostingResponse toResponse(PostingResult result);

    @Mapping(target = "entryId", source = "id")
    @Mapping(target = "amount", qualifiedByName = MONEY)
    @Mapping(target = "balanceAfter", qualifiedByName = MONEY)
    LedgerEntryResponse toEntryResponse(LedgerEntry entry);

    // Oracle tra NUMBER(19,2) bo so 0 cuoi (300000 thay vi 300000.00): dua ve scale 2 de journal moi va journal doc
    // lai (posting lap lai) tra cung mot JSON
    @Named(MONEY)
    default BigDecimal toMoney(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
