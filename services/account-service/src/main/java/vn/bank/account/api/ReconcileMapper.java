package vn.bank.account.api;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import vn.bank.account.application.ReconcileReport;
import vn.bank.account.domain.ReconcileResult;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface ReconcileMapper {

    @Mapping(target = "errorCount", expression = "java(report.errors().size())")
    ReconcileResponse toResponse(ReconcileReport report);

    ReconcileErrorResponse toErrorResponse(ReconcileResult result);
}
