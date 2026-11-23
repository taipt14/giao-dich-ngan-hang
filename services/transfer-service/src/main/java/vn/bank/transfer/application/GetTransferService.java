package vn.bank.transfer.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.bank.common.error.BusinessException;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferErrorCode;
import vn.bank.transfer.infrastructure.persistence.TransferRepository;

/** Chi tiet mot lenh chuyen khoan kem lich su trang thai (da nap san, doc duoc sau khi transaction dong). */
@Service
@RequiredArgsConstructor
public class GetTransferService {

    private final TransferRepository transferRepository;

    /** Lenh khong ton tai / cua khach hang khac → {@code TRANSFER_NOT_FOUND} (khong phan biet de khong lo thong tin). */
    @Transactional(readOnly = true)
    public Transfer getTransfer(String transferId, String cif) {
        return transferRepository
                .findWithStatusHistoryById(transferId)
                .filter(transfer -> transfer.isOwnedBy(cif))
                .orElseThrow(() -> new BusinessException(
                        TransferErrorCode.TRANSFER_NOT_FOUND, "Transfer not found: " + transferId));
    }
}
