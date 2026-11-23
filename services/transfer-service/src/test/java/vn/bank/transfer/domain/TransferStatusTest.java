package vn.bank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static vn.bank.transfer.domain.TransferStatus.FAILED;
import static vn.bank.transfer.domain.TransferStatus.INITIATED;
import static vn.bank.transfer.domain.TransferStatus.OTP_PENDING;
import static vn.bank.transfer.domain.TransferStatus.PENDING_NAPAS;
import static vn.bank.transfer.domain.TransferStatus.PROCESSING;
import static vn.bank.transfer.domain.TransferStatus.REVERSED;
import static vn.bank.transfer.domain.TransferStatus.SUCCESS;
import static vn.bank.transfer.domain.TransferStatus.UNKNOWN;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class TransferStatusTest {

    // Bang chuyen trang thai mong doi, viet doc lap voi code (docs/transfer-state.md)
    private static final Map<TransferStatus, Set<TransferStatus>> EXPECTED_TRANSITIONS = Map.of(
            INITIATED, Set.of(OTP_PENDING),
            OTP_PENDING, Set.of(PROCESSING, FAILED),
            PROCESSING, Set.of(SUCCESS, FAILED, PENDING_NAPAS, UNKNOWN),
            PENDING_NAPAS, Set.of(SUCCESS, REVERSED, UNKNOWN),
            UNKNOWN, Set.of(SUCCESS, FAILED, REVERSED),
            SUCCESS, Set.of(),
            FAILED, Set.of(),
            REVERSED, Set.of());

    static List<Arguments> everyStatusPair() {
        var pairs = new ArrayList<Arguments>();
        for (var from : TransferStatus.values()) {
            for (var to : TransferStatus.values()) {
                pairs.add(Arguments.of(from, to, EXPECTED_TRANSITIONS.get(from).contains(to)));
            }
        }
        return pairs;
    }

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @MethodSource("everyStatusPair")
    void should_allowOnlyDefinedTransitions_when_checkingEveryStatusPair(
            TransferStatus from, TransferStatus to, boolean allowed) {
        // given: cap trang thai (from, to), gom ca chuyen hop le va khong hop le

        // when
        var result = from.canTransitionTo(to);

        // then
        assertThat(result).isEqualTo(allowed);
    }

    @ParameterizedTest
    @EnumSource(
            value = TransferStatus.class,
            names = {"SUCCESS", "FAILED", "REVERSED"})
    void should_notTransitionToAnyStatus_when_statusIsFinal(TransferStatus finalStatus) {
        // given: trang thai cuoi

        // when
        var reachable = new ArrayList<TransferStatus>();
        for (var target : TransferStatus.values()) {
            if (finalStatus.canTransitionTo(target)) {
                reachable.add(target);
            }
        }

        // then
        assertThat(finalStatus.isFinal()).isTrue();
        assertThat(reachable).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = TransferStatus.class,
            names = {"SUCCESS", "FAILED", "REVERSED"},
            mode = EnumSource.Mode.EXCLUDE)
    void should_notBeFinal_when_statusIsNotSuccessFailedOrReversed(TransferStatus status) {
        // given: trang thai trung gian, ke ca UNKNOWN (con cho tra soat)

        // when
        var result = status.isFinal();

        // then
        assertThat(result).isFalse();
    }
}
