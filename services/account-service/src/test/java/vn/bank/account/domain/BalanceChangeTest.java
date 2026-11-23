package vn.bank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BalanceChangeTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SOURCE = "100000000001";
    private static final String DEST = "100000000002";
    private static final String FEE_INCOME = "FEE_INCOME";

    @Test
    void should_returnChangePerCustomerAccount_when_journalPosted() {
        // given: A chuyen 300.000 cho B, phi 2.200
        var source = customerAccount(SOURCE, "C000000001");
        var dest = customerAccount(DEST, "C000000002");
        var lines = List.of(
                line(SOURCE, EntryDirection.DEBIT, "302200.00"),
                line(DEST, EntryDirection.CREDIT, "300000.00"),
                line(FEE_INCOME, EntryDirection.CREDIT, "2200.00"));
        var journal = savedJournal(lines);
        source.credit(new BigDecimal("1000000.00"), NOW);
        source.debit(new BigDecimal("302200.00"), NOW);
        dest.credit(new BigDecimal("300000.00"), NOW);

        // when
        var changes =
                BalanceChange.of(journal, lines, Map.of(SOURCE, source, DEST, dest, FEE_INCOME, glAccount(FEE_INCOME)));

        // then: tai khoan GL khong co bien dong
        assertThat(changes)
                .extracting(BalanceChange::accountNo, BalanceChange::direction, BalanceChange::amount)
                .containsExactly(
                        tuple(SOURCE, EntryDirection.DEBIT, new BigDecimal("302200.00")),
                        tuple(DEST, EntryDirection.CREDIT, new BigDecimal("300000.00")));
        var sourceChange = changes.getFirst();
        assertThat(sourceChange.cif()).isEqualTo("C000000001");
        assertThat(sourceChange.journalId()).isEqualTo(7L);
        assertThat(sourceChange.referenceType()).isEqualTo(ReferenceType.TRANSFER);
        assertThat(sourceChange.referenceId()).isEqualTo("T-1");
        assertThat(sourceChange.balanceAfter()).isEqualByComparingTo("697800.00");
        assertThat(sourceChange.currency()).isEqualTo("VND");
        assertThat(sourceChange.description()).isEqualTo("Chuyen tien");
        assertThat(sourceChange.changedAt()).isEqualTo(NOW);
    }

    @Test
    void should_mergeLinesOfSameAccount_when_accountAppearsTwice() {
        // given: cung tai khoan co 2 dong Co
        var account = customerAccount(DEST, "C000000002");
        var lines = List.of(
                line("CASH_VAULT", EntryDirection.DEBIT, "300.00"),
                line(DEST, EntryDirection.CREDIT, "100.00"),
                line(DEST, EntryDirection.CREDIT, "200.00"));

        // when
        var changes = BalanceChange.of(
                savedJournal(lines), lines, Map.of(DEST, account, "CASH_VAULT", glAccount("CASH_VAULT")));

        // then
        assertThat(changes).singleElement().satisfies(change -> {
            assertThat(change.direction()).isEqualTo(EntryDirection.CREDIT);
            assertThat(change.amount()).isEqualByComparingTo("300.00");
        });
    }

    @Test
    void should_skipAccount_when_netChangeIsZero() {
        // given: No va Co cung so tien tren cung tai khoan
        var account = customerAccount(DEST, "C000000002");
        var lines = List.of(line(DEST, EntryDirection.DEBIT, "100.00"), line(DEST, EntryDirection.CREDIT, "100.00"));

        // when
        var changes = BalanceChange.of(savedJournal(lines), lines, Map.of(DEST, account));

        // then
        assertThat(changes).isEmpty();
    }

    private static Journal savedJournal(List<PostingLine> lines) {
        var journal = Journal.create(ReferenceType.TRANSFER, "T-1", "Chuyen tien", lines, NOW);
        ReflectionTestUtils.setField(journal, "id", 7L);
        return journal;
    }

    private static Account customerAccount(String accountNo, String cif) {
        return Account.open(accountNo, new AccountHolder(cif, "ACTIVE"), AccountType.CURRENT, 0, CLOCK);
    }

    private static Account glAccount(String accountNo) {
        var account = customerAccount(accountNo, "C000000009");
        ReflectionTestUtils.setField(account, "cif", null);
        ReflectionTestUtils.setField(account, "accountType", AccountType.GL);
        return account;
    }

    private static PostingLine line(String accountNo, EntryDirection direction, String amount) {
        return new PostingLine(accountNo, direction, new BigDecimal(amount));
    }
}
