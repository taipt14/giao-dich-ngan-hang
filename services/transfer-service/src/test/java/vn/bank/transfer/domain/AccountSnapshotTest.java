package vn.bank.transfer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.bank.common.error.BusinessException;

class AccountSnapshotTest {

    private static final String ACCOUNT_NO = "100000000001";
    private static final String CIF = "C000000001";

    @Test
    void should_pass_when_sourceOwnedByCustomer() {
        // given
        var account = new AccountSnapshot(ACCOUNT_NO, CIF, "ACTIVE");

        // when / then
        assertThatCode(() -> account.requireOwnedBy(CIF)).doesNotThrowAnyException();
    }

    @Test
    void should_rejectWithSourceAccountAccessDenied_when_sourceOwnedByOtherCustomer() {
        // given
        var account = new AccountSnapshot(ACCOUNT_NO, "C000000002", "ACTIVE");

        // when
        var thrown = catchThrowable(() -> account.requireOwnedBy(CIF));

        // then
        assertErrorCode(thrown, TransferErrorCode.SOURCE_ACCOUNT_ACCESS_DENIED);
    }

    @Test
    void should_rejectWithSourceAccountAccessDenied_when_sourceIsGlAccount() {
        // given
        var account = new AccountSnapshot("CASH_VAULT", null, "ACTIVE");

        // when
        var thrown = catchThrowable(() -> account.requireOwnedBy(CIF));

        // then
        assertErrorCode(thrown, TransferErrorCode.SOURCE_ACCOUNT_ACCESS_DENIED);
    }

    @Test
    void should_pass_when_destinationActiveCustomerAccount() {
        // given
        var account = new AccountSnapshot(ACCOUNT_NO, CIF, "ACTIVE");

        // when / then
        assertThatCode(account::requireActiveDestination).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOCKED", "CLOSED"})
    void should_rejectWithDestAccountNotActive_when_destinationNotActive(String status) {
        // given
        var account = new AccountSnapshot(ACCOUNT_NO, CIF, status);

        // when
        var thrown = catchThrowable(account::requireActiveDestination);

        // then
        assertErrorCode(thrown, TransferErrorCode.DEST_ACCOUNT_NOT_ACTIVE);
    }

    @Test
    void should_rejectWithDestAccountNotFound_when_destinationIsGlAccount() {
        // given
        var account = new AccountSnapshot("FEE_INCOME", null, "ACTIVE");

        // when
        var thrown = catchThrowable(account::requireActiveDestination);

        // then
        assertErrorCode(thrown, TransferErrorCode.DEST_ACCOUNT_NOT_FOUND);
    }

    private static void assertErrorCode(Throwable thrown, TransferErrorCode code) {
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }
}
