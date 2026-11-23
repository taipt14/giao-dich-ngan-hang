package vn.bank.transfer.infrastructure.messaging;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import vn.bank.common.events.transfer.TransferCompleted;
import vn.bank.common.events.transfer.TransferFailed;
import vn.bank.transfer.domain.Transfer;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface TransferEventMapper {

    // Viet tay nhu AccountEventMapper: method MapStruct sinh cho 10-12 field dai qua 30 dong, Checkstyle quet ca code
    // sinh
    default TransferCompleted toTransferCompleted(Transfer transfer) {
        return new TransferCompleted(
                transfer.getTransferId(),
                transfer.getType().name(),
                transfer.getCif(),
                transfer.getSourceAccount(),
                transfer.getDestAccount(),
                transfer.getDestBankCode(),
                transfer.getAmount(),
                transfer.getFee(),
                transfer.getDescription(),
                transfer.getUpdatedAt());
    }

    default TransferFailed toTransferFailed(Transfer transfer) {
        return new TransferFailed(
                transfer.getTransferId(),
                transfer.getType().name(),
                transfer.getCif(),
                transfer.getSourceAccount(),
                transfer.getDestAccount(),
                transfer.getDestBankCode(),
                transfer.getAmount(),
                transfer.getFee(),
                transfer.getDescription(),
                transfer.getStatus().name(),
                transfer.getFailureCode(),
                transfer.getUpdatedAt());
    }
}
