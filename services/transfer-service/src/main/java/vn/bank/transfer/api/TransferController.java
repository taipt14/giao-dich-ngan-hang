package vn.bank.transfer.api;

import jakarta.validation.Valid;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.bank.common.error.BusinessException;
import vn.bank.transfer.application.CreateTransferCommand;
import vn.bank.transfer.application.CreateTransferService;
import vn.bank.transfer.domain.TransferErrorCode;

/**
 * Khach hang tao lenh chuyen khoan. Lenh moi tra 201; gui lai cung {@code Idempotency-Key} va cung noi dung tra 200
 * kem lenh cu, khong tao lenh moi.
 */
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
class TransferController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    // Gateway dat header nay tu claim cif cua JWT (BANK-011); user noi bo khong co CIF thi khong co header
    static final String CIF_HEADER = "X-Cif";

    // Dang chuan 8-4-4-4-12 (UUID.fromString chap nhan ca "1-1-1-1-1"); vua cot IDEMPOTENCY_KEY VARCHAR2(36)
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final CreateTransferService createTransferService;
    private final TransferMapper transferMapper;

    // Header doc voi required=false de tra ma loi rieng IDEMPOTENCY_KEY_REQUIRED thay vi loi thieu header chung
    @PostMapping
    @RequiresRole(Role.CUSTOMER)
    ResponseEntity<TransferResponse> create(
            @RequestHeader(name = CIF_HEADER, required = false) String cif,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest request) {
        var command = new CreateTransferCommand(
                requireCif(cif),
                requireIdempotencyKey(idempotencyKey),
                request.sourceAccount(),
                request.destAccount(),
                request.destBankCode(),
                request.amount(),
                request.description());
        var result = createTransferService.create(command);
        var status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(transferMapper.toResponse(result.transfer()));
    }

    private static String requireCif(String cif) {
        if (cif == null) {
            throw new BusinessException(TransferErrorCode.ACCESS_DENIED, "Authenticated user has no CIF");
        }
        return cif;
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(
                    TransferErrorCode.IDEMPOTENCY_KEY_REQUIRED, "Header " + IDEMPOTENCY_KEY_HEADER + " is required");
        }
        if (!UUID_PATTERN.matcher(idempotencyKey).matches()) {
            throw new BusinessException(
                    TransferErrorCode.IDEMPOTENCY_KEY_INVALID, "Header " + IDEMPOTENCY_KEY_HEADER + " must be a UUID");
        }
        return idempotencyKey;
    }
}
