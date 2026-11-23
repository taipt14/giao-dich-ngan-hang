package vn.bank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.bank.common.error.BusinessException;

class JournalTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");
    private static final String REFERENCE_ID = "6f1c2d6e-0000-4000-8000-000000000001";

    @Test
    void should_createJournal_when_totalDebitEqualsTotalCredit() {
        // given: chuyen 300.000, phi 2.200 (docs/ledger.md 4.2)
        var lines = List.of(
                line("100000000001", EntryDirection.DEBIT, "302200.00"),
                line("100000000002", EntryDirection.CREDIT, "300000.00"),
                line("FEE_INCOME", EntryDirection.CREDIT, "2200"));

        // when
        var journal = Journal.create(ReferenceType.TRANSFER, REFERENCE_ID, "Chuyen tien", lines, NOW);

        // then
        assertThat(journal.getReferenceType()).isEqualTo(ReferenceType.TRANSFER);
        assertThat(journal.getReferenceId()).isEqualTo(REFERENCE_ID);
        assertThat(journal.getDescription()).isEqualTo("Chuyen tien");
        assertThat(journal.getCreatedAt()).isEqualTo(NOW);
        assertThat(journal.getId()).isNull();
    }

    @Test
    void should_rejectJournalNotBalanced_when_totalDebitDiffersFromTotalCredit() {
        // given
        var lines = List.of(
                line("100000000001", EntryDirection.DEBIT, "300000.00"),
                line("100000000002", EntryDirection.CREDIT, "299999.99"));

        // when
        var thrown = catchThrowable(() -> create(lines));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.JOURNAL_NOT_BALANCED);
    }

    @Test
    void should_rejectJournalNotBalanced_when_onlyOneLine() {
        // given
        var lines = List.of(line("100000000001", EntryDirection.CREDIT, "100.00"));

        // when
        var thrown = catchThrowable(() -> create(lines));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.JOURNAL_NOT_BALANCED);
    }

    @Test
    void should_rejectJournalNotBalanced_when_noLines() {
        // when
        var thrown = catchThrowable(() -> create(List.of()));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.JOURNAL_NOT_BALANCED);
    }

    @Test
    void should_rejectInvalidAmount_when_amountNotPositive() {
        // given: tong van can bang nhung co dong am
        var lines = List.of(
                line("100000000001", EntryDirection.DEBIT, "-100.00"),
                line("100000000002", EntryDirection.CREDIT, "-100.00"));

        // when
        var thrown = catchThrowable(() -> create(lines));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.INVALID_AMOUNT);
    }

    @Test
    void should_rejectInvalidAmount_when_moreThanTwoDecimalPlaces() {
        // given
        var lines = List.of(
                line("100000000001", EntryDirection.DEBIT, "100.001"),
                line("100000000002", EntryDirection.CREDIT, "100.001"));

        // when
        var thrown = catchThrowable(() -> create(lines));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.INVALID_AMOUNT);
    }

    private static Journal create(List<PostingLine> lines) {
        return Journal.create(ReferenceType.TRANSFER, REFERENCE_ID, null, lines, NOW);
    }

    private static PostingLine line(String accountNo, EntryDirection direction, String amount) {
        return new PostingLine(accountNo, direction, new BigDecimal(amount));
    }
}
