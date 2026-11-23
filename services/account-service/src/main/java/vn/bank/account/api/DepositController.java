package vn.bank.account.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.bank.account.application.CreateDepositService;
import vn.bank.account.application.DepositCommand;

/**
 * ADMIN nap tien mat tai quay. Lan nap moi tra 201; gui lai cung {@code Idempotency-Key} tra 200 kem lan nap cu, khong
 * nap them.
 */
@RestController
@RequestMapping("/api/v1/accounts/{accountNo}/deposits")
@RequiredArgsConstructor
class DepositController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    // Idempotency-Key luu vao JOURNAL.REFERENCE_ID VARCHAR2(64)
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private final CreateDepositService createDepositService;
    private final DepositMapper depositMapper;

    @PostMapping
    @RequiresRole(Role.ADMIN)
    ResponseEntity<DepositResponse> deposit(
            @PathVariable String accountNo,
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @NotBlank @Size(max = MAX_IDEMPOTENCY_KEY_LENGTH)
                    String idempotencyKey,
            @Valid @RequestBody CreateDepositRequest request) {
        var command = new DepositCommand(accountNo, idempotencyKey, request.amount(), request.description());
        var result = createDepositService.deposit(command);
        var status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(depositMapper.toResponse(result));
    }
}
