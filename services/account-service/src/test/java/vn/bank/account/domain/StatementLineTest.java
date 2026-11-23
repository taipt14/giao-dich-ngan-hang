package vn.bank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StatementLineTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SOURCE = "100000000001";
    private static final String DEST = "100000000002";
    private static final BigDecimal AMOUNT = new BigDecimal("300000.00");

    @Test
    void should_returnNegativeAmountAndDebit_when_entryIsDebit() {
        // given
        var journal = journal(7L);
        var source = funded(SOURCE, "1000000.00");
        var entry = LedgerEntry.post(journal, source, new PostingLine(SOURCE, EntryDirection.DEBIT, AMOUNT));

        // when
        var line = StatementLine.of(entry, journal);

        // then
        assertThat(line.direction()).isEqualTo(EntryDirection.DEBIT);
        assertThat(line.amount()).isEqualByComparingTo("-300000.00");
        assertThat(line.balanceAfter()).isEqualByComparingTo("700000.00");
        assertThat(line.referenceType()).isEqualTo(ReferenceType.TRANSFER);
        assertThat(line.description()).isEqualTo("Chuyen tien");
        assertThat(line.createdAt()).isEqualTo(NOW);
    }

    @Test
    void should_returnPositiveAmountAndCredit_when_entryIsCredit() {
        // given
        var journal = journal(7L);
        var dest = funded(DEST, "0.00");
        var entry = LedgerEntry.post(journal, dest, new PostingLine(DEST, EntryDirection.CREDIT, AMOUNT));

        // when
        var line = StatementLine.of(entry, journal);

        // then
        assertThat(line.direction()).isEqualTo(EntryDirection.CREDIT);
        assertThat(line.amount()).isEqualByComparingTo("300000.00");
        assertThat(line.balanceAfter()).isEqualByComparingTo("300000.00");
    }

    @Test
    void should_reject_when_journalDoesNotContainEntry() {
        // given
        var dest = funded(DEST, "0.00");
        var entry = LedgerEntry.post(journal(7L), dest, new PostingLine(DEST, EntryDirection.CREDIT, AMOUNT));

        // when
        var thrown = catchThrowable(() -> StatementLine.of(entry, journal(8L)));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    private static Journal journal(long id) {
        var lines = List.of(
                new PostingLine(SOURCE, EntryDirection.DEBIT, AMOUNT),
                new PostingLine(DEST, EntryDirection.CREDIT, AMOUNT));
        var journal = Journal.create(ReferenceType.TRANSFER, "TRF-" + id, "Chuyen tien", lines, NOW);
        ReflectionTestUtils.setField(journal, "id", id);
        return journal;
    }

    private static Account funded(String accountNo, String balance) {
        var account = Account.open(accountNo, new AccountHolder("C000000001", "ACTIVE"), AccountType.CURRENT, 0, CLOCK);
        var amount = new BigDecimal(balance);
        if (amount.signum() > 0) {
            account.credit(amount, NOW);
        }
        return account;
    }
}
