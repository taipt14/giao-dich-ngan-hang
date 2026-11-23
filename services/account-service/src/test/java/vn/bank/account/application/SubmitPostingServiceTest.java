package vn.bank.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import vn.bank.account.domain.EntryDirection;
import vn.bank.account.domain.Journal;
import vn.bank.account.domain.PostingLine;
import vn.bank.account.domain.ReferenceType;

class SubmitPostingServiceTest {

    private static final String REFERENCE_ID = "6f1c2d6e-0000-4000-8000-000000000002";
    private static final List<PostingLine> LINES = List.of(
            new PostingLine("CASH_VAULT", EntryDirection.DEBIT, new BigDecimal("100.00")),
            new PostingLine("100000000001", EntryDirection.CREDIT, new BigDecimal("100.00")));
    private static final PostingCommand COMMAND =
            new PostingCommand(ReferenceType.DEPOSIT, REFERENCE_ID, "Nap tien", LINES);

    private final PostingService postingService = mock(PostingService.class);
    private final SubmitPostingService service = new SubmitPostingService(postingService);

    @Test
    void should_returnExistingJournal_when_concurrentPostingWithSameReferenceCommittedFirst() {
        // given
        var existing = new PostingResult(
                Journal.create(ReferenceType.DEPOSIT, REFERENCE_ID, "Nap tien", LINES, Instant.EPOCH),
                List.of(),
                false);
        when(postingService.post(COMMAND))
                .thenThrow(new DataIntegrityViolationException("ORA-00001: UK_JOURNAL_REFERENCE_ID_TYPE"));
        when(postingService.findPosted(ReferenceType.DEPOSIT, REFERENCE_ID)).thenReturn(Optional.of(existing));

        // when
        var result = service.submit(COMMAND);

        // then
        assertThat(result).isSameAs(existing);
    }

    @Test
    void should_rethrow_when_integrityViolationIsNotDuplicateReference() {
        // given
        var violation = new DataIntegrityViolationException("ORA-02291: FK_LEDGER_ENTRY_ACCOUNT_NO");
        when(postingService.post(COMMAND)).thenThrow(violation);
        when(postingService.findPosted(ReferenceType.DEPOSIT, REFERENCE_ID)).thenReturn(Optional.empty());

        // when
        var thrown = catchThrowable(() -> service.submit(COMMAND));

        // then
        assertThat(thrown).isSameAs(violation);
    }
}
