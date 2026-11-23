package vn.bank.transfer.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.bank.transfer.domain.TransferPeriod;
import vn.bank.transfer.infrastructure.persistence.TransferRepository;

/**
 * Lich su lenh chuyen khoan cua khach hang, phan trang theo offset ({@code page}/{@code size}, kem
 * {@code totalElements}), moi nhat truoc. Chi tra lenh co CIF cua khach hang dang dang nhap.
 */
@Service
@RequiredArgsConstructor
public class SearchTransfersService {

    private final TransferRepository transferRepository;

    /** {@code from > to} → {@code TRANSFER_RANGE_INVALID}. */
    @Transactional(readOnly = true)
    public TransferPage search(TransferSearchQuery query) {
        var period = TransferPeriod.of(query.from(), query.to());
        var result = transferRepository.findPageOfCustomer(
                query.cif(),
                query.status(),
                period.startInclusive(),
                period.endExclusive(),
                PageRequest.of(query.page(), query.size()));
        return new TransferPage(result.getContent(), query.page(), query.size(), result.getTotalElements());
    }
}
