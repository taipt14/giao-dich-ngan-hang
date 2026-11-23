package vn.bank.account.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Posting idempotent theo {@code (referenceType, referenceId)}, ke ca khi hai request cung reference den dong thoi
 * (client retry sau timeout): ben commit sau gap UK khi INSERT JOURNAL va transaction cua no da rollback, nen doc lai
 * journal cua ben commit truoc trong transaction moi roi tra ve nhu mot lan goi lap lai.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmitPostingService {

    private final PostingService postingService;

    public PostingResult submit(PostingCommand command) {
        try {
            return postingService.post(command);
        } catch (DataIntegrityViolationException e) {
            // Khong co journal: loi rang buoc khac, khong phai trung reference, nem lai nguyen loi
            var existing = postingService
                    .findPosted(command.referenceType(), command.referenceId())
                    .orElseThrow(() -> e);
            log.warn(
                    "Concurrent posting for {} {} resolved to existing journal {}",
                    command.referenceType(),
                    command.referenceId(),
                    existing.journal().getId());
            return existing;
        }
    }
}
