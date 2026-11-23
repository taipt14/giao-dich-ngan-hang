package vn.bank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.bank.common.error.BusinessException;

class TransferTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-23T03:00:00Z");
    private static final Instant CHANGED_AT = Instant.parse("2026-11-23T03:01:00Z");
    private static final Clock CREATE_CLOCK = Clock.fixed(CREATED_AT, ZoneOffset.UTC);
    private static final Clock CLOCK = Clock.fixed(CHANGED_AT, ZoneOffset.UTC);
    private static final String TRANSFER_ID = "7f1c2a9e-1d2b-4c3d-9e8f-0a1b2c3d4e5f";
    private static final String NAPAS_BANK_CODE = "970436";
    private static final String OTP_MAX_ATTEMPTS = "OTP_MAX_ATTEMPTS";

    @Test
    void should_createInitiatedInternalTransferWithFirstHistory_when_initiated() {
        // given
        var request = newTransfer(null, "300000", "0");

        // when
        var transfer = Transfer.initiate(TRANSFER_ID, request, CREATE_CLOCK);

        // then
        assertThat(transfer.getTransferId()).isEqualTo(TRANSFER_ID);
        assertThat(transfer.getType()).isEqualTo(TransferType.INTERNAL);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.INITIATED);
        assertThat(transfer.getAmount()).isEqualTo(new BigDecimal("300000.00"));
        assertThat(transfer.getFee()).isEqualTo(new BigDecimal("0.00"));
        assertThat(transfer.getFailureCode()).isNull();
        assertThat(transfer.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(transfer.getUpdatedAt()).isEqualTo(CREATED_AT);
        assertThat(transfer.getStatusHistory())
                .extracting("previousStatus", "status", "failureCode", "changedAt")
                .containsExactly(tuple(null, TransferStatus.INITIATED, null, CREATED_AT));
    }

    @Test
    void should_createInterbankTransfer_when_destBankCodeGiven() {
        // given
        var request = newTransfer(NAPAS_BANK_CODE, "500000", "2200");

        // when
        var transfer = Transfer.initiate(TRANSFER_ID, request, CREATE_CLOCK);

        // then
        assertThat(transfer.getType()).isEqualTo(TransferType.INTERBANK);
        assertThat(transfer.getDestBankCode()).isEqualTo(NAPAS_BANK_CODE);
        assertThat(transfer.getFee()).isEqualTo(new BigDecimal("2200.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1"})
    void should_rejectInitiate_when_amountNotPositive(String amount) {
        // given
        var request = newTransfer(null, amount, "0");

        // when
        var thrown = catchThrowable(() -> Transfer.initiate(TRANSFER_ID, request, CREATE_CLOCK));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.INVALID_AMOUNT);
    }

    @Test
    void should_rejectInitiate_when_feeNegative() {
        // given
        var request = newTransfer(null, "300000", "-1");

        // when
        var thrown = catchThrowable(() -> Transfer.initiate(TRANSFER_ID, request, CREATE_CLOCK));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_recordEveryStatusChangeInHistory_when_internalTransferSucceeds() {
        // given
        var transfer = internalTransfer();

        // when
        transfer.changeStatus(TransferStatus.OTP_PENDING, CLOCK);
        transfer.changeStatus(TransferStatus.PROCESSING, CLOCK);
        transfer.changeStatus(TransferStatus.SUCCESS, CLOCK);

        // then
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(transfer.getUpdatedAt()).isEqualTo(CHANGED_AT);
        assertThat(transfer.getStatusHistory())
                .extracting("previousStatus", "status", "changedAt")
                .containsExactly(
                        tuple(null, TransferStatus.INITIATED, CREATED_AT),
                        tuple(TransferStatus.INITIATED, TransferStatus.OTP_PENDING, CHANGED_AT),
                        tuple(TransferStatus.OTP_PENDING, TransferStatus.PROCESSING, CHANGED_AT),
                        tuple(TransferStatus.PROCESSING, TransferStatus.SUCCESS, CHANGED_AT));
    }

    @Test
    void should_storeFailureCodeOnTransferAndHistory_when_transferFailed() {
        // given
        var transfer = internalTransfer();
        transfer.changeStatus(TransferStatus.OTP_PENDING, CLOCK);

        // when
        transfer.fail(OTP_MAX_ATTEMPTS, CLOCK);

        // then
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getFailureCode()).isEqualTo(OTP_MAX_ATTEMPTS);
        assertThat(transfer.getStatusHistory().getLast())
                .extracting("previousStatus", "status", "failureCode")
                .containsExactly(TransferStatus.OTP_PENDING, TransferStatus.FAILED, OTP_MAX_ATTEMPTS);
    }

    @Test
    void should_reverseInterbankTransfer_when_pendingNapasRejected() {
        // given
        var transfer = transferIn(TransferStatus.PENDING_NAPAS, TransferType.INTERBANK);

        // when
        transfer.changeStatus(TransferStatus.REVERSED, CLOCK);

        // then
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.REVERSED);
        assertThat(transfer.isFinal()).isTrue();
        assertThat(transfer.getStatusHistory())
                .extracting("status")
                .containsExactly(
                        TransferStatus.INITIATED,
                        TransferStatus.OTP_PENDING,
                        TransferStatus.PROCESSING,
                        TransferStatus.PENDING_NAPAS,
                        TransferStatus.REVERSED);
    }

    @Test
    void should_rejectAndKeepState_when_transitionNotAllowed() {
        // given
        var transfer = internalTransfer();

        // when
        var thrown = catchThrowable(() -> transfer.changeStatus(TransferStatus.SUCCESS, CLOCK));

        // then
        assertInvalidTransition(thrown);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.INITIATED);
        assertThat(transfer.getUpdatedAt()).isEqualTo(CREATED_AT);
        assertThat(transfer.getStatusHistory()).hasSize(1);
    }

    static List<Arguments> finalStatusAndEveryTarget() {
        var cases = new ArrayList<Arguments>();
        for (var finalStatus : List.of(TransferStatus.SUCCESS, TransferStatus.FAILED, TransferStatus.REVERSED)) {
            for (var target : TransferStatus.values()) {
                cases.add(Arguments.of(finalStatus, target));
            }
        }
        return cases;
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("finalStatusAndEveryTarget")
    void should_rejectAnyTransition_when_transferIsInFinalStatus(TransferStatus finalStatus, TransferStatus target) {
        // given: lenh lien ngan hang (ho tro moi trang thai) da o trang thai cuoi
        var transfer = transferIn(finalStatus, TransferType.INTERBANK);
        var historySize = transfer.getStatusHistory().size();

        // when
        var thrown = catchThrowable(() -> moveTo(transfer, target));

        // then
        assertInvalidTransition(thrown);
        assertThat(transfer.getStatus()).isEqualTo(finalStatus);
        assertThat(transfer.getStatusHistory()).hasSize(historySize);
    }

    @Test
    void should_rejectPendingNapas_when_transferIsInternal() {
        // given
        var transfer = transferIn(TransferStatus.PROCESSING, TransferType.INTERNAL);

        // when
        var thrown = catchThrowable(() -> transfer.changeStatus(TransferStatus.PENDING_NAPAS, CLOCK));

        // then
        assertInvalidTransition(thrown);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PROCESSING);
    }

    @Test
    void should_rejectReversed_when_transferIsInternal() {
        // given
        var transfer = transferIn(TransferStatus.UNKNOWN, TransferType.INTERNAL);

        // when
        var thrown = catchThrowable(() -> transfer.changeStatus(TransferStatus.REVERSED, CLOCK));

        // then
        assertInvalidTransition(thrown);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.UNKNOWN);
    }

    @Test
    void should_failInternalTransfer_when_unknownResolvedAsNotDebited() {
        // given
        var transfer = transferIn(TransferStatus.UNKNOWN, TransferType.INTERNAL);

        // when
        transfer.fail("POSTING_NOT_FOUND", CLOCK);

        // then
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
    }

    @Test
    void should_requireFailMethod_when_changingStatusToFailed() {
        // given
        var transfer = transferIn(TransferStatus.OTP_PENDING, TransferType.INTERNAL);

        // when
        var thrown = catchThrowable(() -> transfer.changeStatus(TransferStatus.FAILED, CLOCK));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.OTP_PENDING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void should_rejectFail_when_failureCodeBlank(String failureCode) {
        // given
        var transfer = transferIn(TransferStatus.OTP_PENDING, TransferType.INTERNAL);

        // when
        var thrown = catchThrowable(() -> transfer.fail(failureCode, CLOCK));

        // then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.OTP_PENDING);
    }

    @Test
    void should_acceptOtp_when_transferOtpPending() {
        // given
        var transfer = internalTransfer();
        transfer.changeStatus(TransferStatus.OTP_PENDING, CLOCK);

        // when
        var thrown = catchThrowable(transfer::requireAwaitingOtp);

        // then
        assertThat(thrown).isNull();
        assertThat(transfer.isAwaitingOtp()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INITIATED", "PROCESSING", "FAILED"})
    void should_rejectWithTransferNotAwaitingOtp_when_transferNotOtpPending(TransferStatus status) {
        // given
        var transfer = transferIn(status, TransferType.INTERNAL);

        // when
        var thrown = catchThrowable(transfer::requireAwaitingOtp);

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.TRANSFER_NOT_AWAITING_OTP);
    }

    @Test
    void should_beOwnedOnlyByCreatingCustomer_when_checkingOwner() {
        // given
        var transfer = internalTransfer();

        // when / then
        assertThat(transfer.isOwnedBy("C000000001")).isTrue();
        assertThat(transfer.isOwnedBy("C000000002")).isFalse();
    }

    private static void assertInvalidTransition(Throwable thrown) {
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.INVALID_STATUS_TRANSITION);
    }

    private static void moveTo(Transfer transfer, TransferStatus target) {
        if (target == TransferStatus.FAILED) {
            transfer.fail(OTP_MAX_ATTEMPTS, CLOCK);
        } else {
            transfer.changeStatus(target, CLOCK);
        }
    }

    // Dua lenh toi trang thai mong muon qua duong chuyen hop le
    private static Transfer transferIn(TransferStatus status, TransferType type) {
        var transfer = type == TransferType.INTERNAL ? internalTransfer() : interbankTransfer();
        var path = switch (status) {
            case INITIATED -> List.<TransferStatus>of();
            case OTP_PENDING -> List.of(TransferStatus.OTP_PENDING);
            case PROCESSING -> List.of(TransferStatus.OTP_PENDING, TransferStatus.PROCESSING);
            case SUCCESS, UNKNOWN, PENDING_NAPAS ->
                List.of(TransferStatus.OTP_PENDING, TransferStatus.PROCESSING, status);
            case FAILED -> List.of(TransferStatus.OTP_PENDING, TransferStatus.FAILED);
            case REVERSED ->
                List.of(
                        TransferStatus.OTP_PENDING,
                        TransferStatus.PROCESSING,
                        TransferStatus.PENDING_NAPAS,
                        TransferStatus.REVERSED);
        };
        path.forEach(step -> moveTo(transfer, step));
        return transfer;
    }

    private static Transfer internalTransfer() {
        return Transfer.initiate(TRANSFER_ID, newTransfer(null, "300000", "0"), CREATE_CLOCK);
    }

    private static Transfer interbankTransfer() {
        return Transfer.initiate(TRANSFER_ID, newTransfer(NAPAS_BANK_CODE, "300000", "2200"), CREATE_CLOCK);
    }

    private static NewTransfer newTransfer(String destBankCode, String amount, String fee) {
        return new NewTransfer(
                "C000000001",
                "0b9f3c1e-5a7d-4e2f-8c6b-1d2e3f4a5b6c",
                "100000000001",
                "100000000002",
                destBankCode,
                new BigDecimal(amount),
                new BigDecimal(fee),
                "Chuyen tien");
    }
}
