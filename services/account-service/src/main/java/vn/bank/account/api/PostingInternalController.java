package vn.bank.account.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.bank.account.application.FindPostingsService;
import vn.bank.account.application.SubmitPostingService;

/**
 * API noi bo ghi but toan cho service khac (transfer-service), khong route qua gateway (muc 10 convention). Journal moi
 * tra 201; reference da co journal tra 200 kem journal cu, de client retry an toan. Tra soat theo {@code referenceId}
 * tra 200 kem danh sach journal (rong neu chua ghi).
 */
@RestController
@RequestMapping("/internal/postings")
@RequiredArgsConstructor
class PostingInternalController {

    // Bang do dai cot JOURNAL.REFERENCE_ID
    private static final int REFERENCE_ID_MAX_LENGTH = 64;

    private final SubmitPostingService submitPostingService;
    private final FindPostingsService findPostingsService;
    private final PostingMapper postingMapper;

    @PostMapping
    ResponseEntity<PostingResponse> post(@Valid @RequestBody CreatePostingRequest request) {
        var result = submitPostingService.submit(postingMapper.toCommand(request));
        var status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(postingMapper.toResponse(result));
    }

    @GetMapping
    PostingListResponse findByReferenceId(
            @RequestParam @NotBlank @Size(max = REFERENCE_ID_MAX_LENGTH) String referenceId) {
        var items = findPostingsService.findByReferenceId(referenceId).stream()
                .map(postingMapper::toResponse)
                .toList();
        return new PostingListResponse(items);
    }
}
