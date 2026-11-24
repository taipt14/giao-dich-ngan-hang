package vn.bank.transfer.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.bank.common.error.BusinessException;
import vn.bank.transfer.application.ConfirmOtpService;
import vn.bank.transfer.application.CreateTransferCommand;
import vn.bank.transfer.application.CreateTransferService;
import vn.bank.transfer.application.GetTransferService;
import vn.bank.transfer.application.ResendOtpService;
import vn.bank.transfer.application.SearchTransfersService;
import vn.bank.transfer.application.TransferSearchQuery;
import vn.bank.transfer.domain.TransferErrorCode;
import vn.bank.transfer.domain.TransferStatus;

/**
 * Khach hang tao lenh chuyen khoan va xac thuc lenh bang OTP. Lenh moi tra 201 o OTP_PENDING (OTP da gui); gui lai
 * cung {@code Idempotency-Key} va cung noi dung tra 200 kem lenh cu, khong tao lenh moi va khong sinh OTP moi.
 */
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
class TransferController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    // Gateway dat header nay tu claim cif cua JWT (BANK-011); user noi bo khong co CIF thi khong co header
    static final String CIF_HEADER = "X-Cif";

    // Dang chuan 8-4-4-4-12 (UUID.fromString chap nhan ca "1-1-1-1-1"); vua cot IDEMPOTENCY_KEY VARCHAR2(36)
    // Muc 10 convention: page tu 0, size mac dinh 20, toi da 100
    private static final String FIRST_PAGE = "0";
    private static final String DEFAULT_PAGE_SIZE = "20";
    private static final int MAX_PAGE_SIZE = 100;

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final CreateTransferService createTransferService;
    private final ConfirmOtpService confirmOtpService;
    private final ResendOtpService resendOtpService;
    private final SearchTransfersService searchTransfersService;
    private final GetTransferService getTransferService;
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

    /**
     * Xac thuc lenh bang OTP; chi chu lenh (CIF cua lenh = {@code X-Cif}). OTP dung tra 200 kem lenh da thuc hien:
     * SUCCESS, FAILED (kem {@code failureCode}) hoac UNKNOWN (cho tra soat); lenh lien ngan hang o PROCESSING.
     */
    @PostMapping("/{transferId}/confirm")
    @RequiresRole(Role.CUSTOMER)
    TransferResponse confirm(
            @RequestHeader(name = CIF_HEADER, required = false) String cif,
            @PathVariable String transferId,
            @Valid @RequestBody ConfirmOtpRequest request) {
        return transferMapper.toResponse(confirmOtpService.confirm(transferId, requireCif(cif), request.otp()));
    }

    /** Gui lai OTP cho lenh OTP_PENDING; chi chu lenh. OTP khong bao gio nam trong response. */
    @PostMapping("/{transferId}/otp/resend")
    @RequiresRole(Role.CUSTOMER)
    TransferResponse resendOtp(
            @RequestHeader(name = CIF_HEADER, required = false) String cif, @PathVariable String transferId) {
        return transferMapper.toResponse(resendOtpService.resend(transferId, requireCif(cif)));
    }

    /**
     * Lich su lenh cua chinh khach hang, moi nhat truoc. {@code from}/{@code to}: ngay nghiep vu gio Viet Nam
     * (yyyy-MM-dd), tinh ca hai dau. So tai khoan dich trong danh sach da che mot phan.
     */
    @GetMapping
    @RequiresRole(Role.CUSTOMER)
    TransferPageResponse list(
            @RequestHeader(name = CIF_HEADER, required = false) String cif,
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = FIRST_PAGE) @Min(0) int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        var query = new TransferSearchQuery(requireCif(cif), status, from, to, page, size);
        return transferMapper.toPageResponse(searchTransfersService.search(query));
    }

    /** Chi tiet lenh kem lich su trang thai; lenh cua khach hang khac → 404 {@code TRANSFER_NOT_FOUND}. */
    @GetMapping("/{transferId}")
    @RequiresRole(Role.CUSTOMER)
    TransferDetailResponse get(
            @RequestHeader(name = CIF_HEADER, required = false) String cif, @PathVariable String transferId) {
        return transferMapper.toDetail(getTransferService.getTransfer(transferId, requireCif(cif)));
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
