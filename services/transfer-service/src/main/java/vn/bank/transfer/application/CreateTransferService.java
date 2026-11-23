package vn.bank.transfer.application;

import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.bank.common.error.BusinessException;
import vn.bank.transfer.domain.AccountNumberMasker;
import vn.bank.transfer.domain.NewTransfer;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferErrorCode;
import vn.bank.transfer.domain.TransferType;
import vn.bank.transfer.infrastructure.client.AccountClient;
import vn.bank.transfer.infrastructure.persistence.TransferRepository;
import vn.bank.transfer.infrastructure.redis.IdempotencyStore;

/**
 * Tao lenh chuyen khoan o trang thai INITIATED, idempotent theo (CIF, Idempotency-Key) bang hai lop: Redis {@code SET
 * NX} chan request trung den cung luc, UK_TRANSFER_CIF_IDEM_KEY trong DB chan khi Redis mat key (het TTL, mat du lieu).
 * Cung key, cung noi dung → tra lenh cu; khac noi dung → {@code IDEMPOTENCY_KEY_REUSED}. Goi account-service truoc,
 * ngoai transaction; ghi DB bang mot lenh {@code saveAndFlush} (transaction cua repository).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateTransferService {

    private final IdempotencyStore idempotencyStore;
    private final AccountClient accountClient;
    private final TransferRepository transferRepository;
    private final TransferFeeEstimator transferFeeEstimator;
    private final Clock clock;

    /**
     * Lenh noi bo cho chinh tai khoan nguon → {@code SAME_ACCOUNT_TRANSFER}; tai khoan nguon khong thuoc {@code cif} →
     * {@code SOURCE_ACCOUNT_ACCESS_DENIED}; tai khoan dich noi bo khong ton tai / khong ACTIVE →
     * {@code DEST_ACCOUNT_NOT_FOUND} / {@code DEST_ACCOUNT_NOT_ACTIVE}; request cung key dang xu ly →
     * {@code IDEMPOTENCY_KEY_IN_PROGRESS}.
     */
    public CreateTransferResult create(CreateTransferCommand command) {
        var fee = transferFeeEstimator.estimate(TransferType.of(command.destBankCode()), command.amount());
        var request = command.toNewTransfer(fee);
        // Kiem tra quy tac cua lenh truoc khi giu key: request sai khong chiem Idempotency-Key
        var transfer = Transfer.initiate(UUID.randomUUID().toString(), request, clock);
        if (!idempotencyStore.tryAcquire(request.cif(), request.idempotencyKey(), request.fingerprint())) {
            return replayAcquiredKey(request);
        }
        var completed = false;
        try {
            var result = createOrReplay(request, transfer);
            completed = true;
            return result;
        } finally {
            if (!completed) {
                idempotencyStore.release(request.cif(), request.idempotencyKey());
            }
        }
    }

    // Request khac da giu key: khac noi dung thi tu choi ngay, cung noi dung thi tra lenh neu da tao xong
    private CreateTransferResult replayAcquiredKey(NewTransfer request) {
        var fingerprint = idempotencyStore.findFingerprint(request.cif(), request.idempotencyKey());
        if (fingerprint.isPresent() && !fingerprint.get().equals(request.fingerprint())) {
            throw keyReused();
        }
        var existing = transferRepository
                .findByCifAndIdempotencyKey(request.cif(), request.idempotencyKey())
                .orElseThrow(() -> new BusinessException(
                        TransferErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS,
                        "Another request with the same Idempotency-Key is being processed"));
        return replay(request, existing);
    }

    private CreateTransferResult createOrReplay(NewTransfer request, Transfer transfer) {
        // Lop DB: Redis da mat key cua lenh tao truoc do
        var existing = transferRepository.findByCifAndIdempotencyKey(request.cif(), request.idempotencyKey());
        if (existing.isPresent()) {
            return replay(request, existing.get());
        }
        requireAccounts(request);
        try {
            var saved = transferRepository.saveAndFlush(transfer);
            log.info(
                    "Transfer {} initiated from account {}",
                    saved.getTransferId(),
                    AccountNumberMasker.mask(saved.getSourceAccount()));
            return new CreateTransferResult(saved, true);
        } catch (DataIntegrityViolationException e) {
            // Vi pham UK_TRANSFER_CIF_IDEM_KEY: request cung key vua tao lenh trong luc Redis khong giu key
            var concurrent = transferRepository
                    .findByCifAndIdempotencyKey(request.cif(), request.idempotencyKey())
                    .orElseThrow(() -> e);
            return replay(request, concurrent);
        }
    }

    private void requireAccounts(NewTransfer request) {
        accountClient
                .findAccount(request.sourceAccount())
                .orElseThrow(() -> new BusinessException(
                        TransferErrorCode.SOURCE_ACCOUNT_ACCESS_DENIED,
                        "Source account not found: " + AccountNumberMasker.mask(request.sourceAccount())))
                .requireOwnedBy(request.cif());
        // Tai khoan dich lien ngan hang nam o ngan hang khac, khong tra duoc qua account-service
        if (request.type() == TransferType.INTERNAL) {
            accountClient
                    .findAccount(request.destAccount())
                    .orElseThrow(() -> new BusinessException(
                            TransferErrorCode.DEST_ACCOUNT_NOT_FOUND,
                            "Destination account not found: " + AccountNumberMasker.mask(request.destAccount())))
                    .requireActiveDestination();
        }
    }

    private CreateTransferResult replay(NewTransfer request, Transfer existing) {
        if (!request.hasSameContentAs(existing)) {
            throw keyReused();
        }
        log.warn("Repeated create transfer request returned existing transfer {}", existing.getTransferId());
        return new CreateTransferResult(existing, false);
    }

    private static BusinessException keyReused() {
        return new BusinessException(
                TransferErrorCode.IDEMPOTENCY_KEY_REUSED, "Idempotency-Key was already used for a different transfer");
    }
}
