package vn.bank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import vn.bank.common.error.BusinessException;

class NewTransferTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC);
    private static final String TRANSFER_ID = "7f1c2a9e-1d2b-4c3d-9e8f-0a1b2c3d4e5f";
    private static final String SOURCE = "100000000001";
    private static final String DEST = "100000000002";
    private static final String NAPAS_BANK_CODE = "970436";
    private static final String DESCRIPTION = "Chuyen tien";

    @Test
    void should_rejectWithSameAccountTransfer_when_internalTransferToSourceAccount() {
        // given
        var request = newTransfer(SOURCE, SOURCE, null, "300000", DESCRIPTION);

        // when
        var thrown = catchThrowable(() -> Transfer.initiate(TRANSFER_ID, request, CLOCK));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(TransferErrorCode.SAME_ACCOUNT_TRANSFER);
    }

    @Test
    void should_allowSameAccountNumber_when_interbankTransfer() {
        // given: cung so tai khoan nhung o ngan hang khac la tai khoan khac
        var request = newTransfer(SOURCE, SOURCE, NAPAS_BANK_CODE, "300000", DESCRIPTION);

        // when
        var transfer = Transfer.initiate(TRANSFER_ID, request, CLOCK);

        // then
        assertThat(request.isSameAccountTransfer()).isFalse();
        assertThat(transfer.getType()).isEqualTo(TransferType.INTERBANK);
    }

    @Test
    void should_haveSameFingerprint_when_amountDiffersOnlyInScale() {
        // given
        var plain = newTransfer(SOURCE, DEST, null, "100000", DESCRIPTION);
        var scaled = newTransfer(SOURCE, DEST, null, "100000.00", DESCRIPTION);

        // when
        var fingerprint = plain.fingerprint();

        // then
        assertThat(fingerprint).isEqualTo(scaled.fingerprint()).hasSize(64);
    }

    @Test
    void should_haveDifferentFingerprint_when_contentDiffers() {
        // given
        var base = newTransfer(SOURCE, DEST, null, "100000", DESCRIPTION);

        // when
        var fingerprint = base.fingerprint();

        // then
        assertThat(fingerprint)
                .isNotEqualTo(
                        newTransfer(SOURCE, DEST, null, "100001", DESCRIPTION).fingerprint())
                .isNotEqualTo(newTransfer(SOURCE, "100000000003", null, "100000", DESCRIPTION)
                        .fingerprint())
                .isNotEqualTo(newTransfer(SOURCE, DEST, NAPAS_BANK_CODE, "100000", DESCRIPTION)
                        .fingerprint())
                .isNotEqualTo(newTransfer(SOURCE, DEST, null, "100000", "Noi dung khac")
                        .fingerprint())
                .isNotEqualTo(newTransfer(SOURCE, DEST, null, "100000", null).fingerprint());
    }

    @Test
    void should_haveDifferentFingerprint_when_fieldBoundaryShifts() {
        // given: noi chuoi khong phan cach se cho hai lenh nay cung mot chuoi
        var shifted = newTransfer(SOURCE + "1", DEST.substring(1), null, "100000", DESCRIPTION);
        var original = newTransfer(SOURCE, DEST, null, "100000", DESCRIPTION);

        // when
        var fingerprint = shifted.fingerprint();

        // then
        assertThat(fingerprint).isNotEqualTo(original.fingerprint());
    }

    @Test
    void should_matchExistingTransfer_when_sameContentDifferentAmountScale() {
        // given
        var existing = Transfer.initiate(TRANSFER_ID, newTransfer(SOURCE, DEST, null, "100000", DESCRIPTION), CLOCK);
        var resent = newTransfer(SOURCE, DEST, null, "100000.00", DESCRIPTION);

        // when
        var same = resent.hasSameContentAs(existing);

        // then
        assertThat(same).isTrue();
    }

    @Test
    void should_notMatchExistingTransfer_when_contentDiffers() {
        // given
        var existing = Transfer.initiate(TRANSFER_ID, newTransfer(SOURCE, DEST, null, "100000", DESCRIPTION), CLOCK);

        // when
        var differentAmount =
                newTransfer(SOURCE, DEST, null, "200000", DESCRIPTION).hasSameContentAs(existing);
        var differentDescription =
                newTransfer(SOURCE, DEST, null, "100000", null).hasSameContentAs(existing);
        var differentBank = newTransfer(SOURCE, DEST, NAPAS_BANK_CODE, "100000", DESCRIPTION)
                .hasSameContentAs(existing);

        // then
        assertThat(differentAmount).isFalse();
        assertThat(differentDescription).isFalse();
        assertThat(differentBank).isFalse();
    }

    private static NewTransfer newTransfer(
            String source, String dest, String destBankCode, String amount, String description) {
        return new NewTransfer(
                "C000000001",
                "0b9f3c1e-5a7d-4e2f-8c6b-1d2e3f4a5b6c",
                source,
                dest,
                destBankCode,
                new BigDecimal(amount),
                BigDecimal.ZERO,
                description);
    }
}
