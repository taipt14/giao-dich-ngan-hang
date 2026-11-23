package vn.bank.account.api;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import vn.bank.account.application.StatementPage;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface StatementMapper {

    StatementResponse toResponse(StatementPage page);
}
