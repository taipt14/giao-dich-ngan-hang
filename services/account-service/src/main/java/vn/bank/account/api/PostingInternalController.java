package vn.bank.account.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.bank.account.application.SubmitPostingService;

/**
 * API noi bo ghi but toan cho service khac (transfer-service), khong route qua gateway (muc 10 convention). Journal moi
 * tra 201; reference da co journal tra 200 kem journal cu, de client retry an toan.
 */
@RestController
@RequestMapping("/internal/postings")
@RequiredArgsConstructor
class PostingInternalController {

    private final SubmitPostingService submitPostingService;
    private final PostingMapper postingMapper;

    @PostMapping
    ResponseEntity<PostingResponse> post(@Valid @RequestBody CreatePostingRequest request) {
        var result = submitPostingService.submit(postingMapper.toCommand(request));
        var status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(postingMapper.toResponse(result));
    }
}
