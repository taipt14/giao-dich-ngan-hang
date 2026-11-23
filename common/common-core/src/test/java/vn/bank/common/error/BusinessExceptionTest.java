package vn.bank.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

    @Test
    void should_keepErrorCodeMessageAndCause_when_createdWithCause() {
        // given
        var cause = new IllegalStateException("row was updated by another transaction");

        // when
        var exception = new BusinessException(CommonErrorCode.VALIDATION_FAILED, "Conflict", cause);

        // then
        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.VALIDATION_FAILED);
        assertThat(exception).hasMessage("Conflict").hasCause(cause);
    }

    @Test
    void should_haveNoCause_when_createdWithoutCause() {
        // when
        var exception = new BusinessException(CommonErrorCode.VALIDATION_FAILED, "Invalid");

        // then
        assertThat(exception).hasMessage("Invalid").hasNoCause();
    }
}
