package vn.bank.transfer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import vn.bank.common.error.BusinessException;
import vn.bank.common.error.ErrorCode;
import vn.bank.common.error.TechnicalException;
import vn.bank.transfer.domain.AccountSnapshot;
import vn.bank.transfer.domain.OtpHash;
import vn.bank.transfer.domain.Transfer;
import vn.bank.transfer.domain.TransferErrorCode;
import vn.bank.transfer.domain.TransferStatus;
import vn.bank.transfer.infrastructure.client.AccountClient;
import vn.bank.transfer.infrastructure.client.OtpSender;
import vn.bank.transfer.infrastructure.persistence.TransferRepository;
import vn.bank.transfer.infrastructure.redis.IdempotencyStore;
import vn.bank.transfer.infrastructure.redis.OtpStore;

class CreateTransferServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CIF = "C000000001";
    private static final String KEY = "0b9f3c1e-5a7d-4e2f-8c6b-1d2e3f4a5b6c";
    private static final String SOURCE = "100000000001";
    private static final String DEST = "100000000002";
    private static final String NAPAS_BANK_CODE = "970436";
    private static final String OTP = "042917";

    private final IdempotencyStore idempotencyStore = mock(IdempotencyStore.class);
    private final AccountClient accountClient = mock(AccountClient.class);
    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final OtpGenerator otpGenerator = mock(OtpGenerator.class);
    private final OtpStore otpStore = mock(OtpStore.class);
    private final OtpSender otpSender = mock(OtpSender.class);
    private final CreateTransferService service = new CreateTransferService(
            idempotencyStore,
            accountClient,
            transferRepository,
            new TransferFeeEstimator(),
            otpGenerator,
            otpStore,
            otpSender,
            CLOCK);

    @BeforeEach
    void stubOtpGenerator() {
        when(otpGenerator.generate()).thenReturn(OTP);
    }

    @Test
    void should_createOtpPendingTransferWithZeroFee_when_accountsValid() {
        // given
        givenKeyAcquired();
        givenAccount(SOURCE, CIF, "ACTIVE");
        givenAccount(DEST, "C000000002", "ACTIVE");
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var result = service.create(command(SOURCE, DEST, null, "500000"));

        // then
        assertThat(result.created()).isTrue();
        var transfer = result.transfer();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.OTP_PENDING);
        assertThat(transfer.getStatusHistory())
                .extracting("status")
                .containsExactly(TransferStatus.INITIATED, TransferStatus.OTP_PENDING);
        assertThat(transfer.getCif()).isEqualTo(CIF);
        assertThat(transfer.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(transfer.getFee()).isEqualByComparingTo("0");
        assertThat(transfer.getCreatedAt()).isEqualTo(NOW);
        verify(idempotencyStore, never()).release(anyString(), anyString());
    }

    @Test
    void should_storeOtpHashAndSendOtp_when_transferCreated() {
        // given
        givenKeyAcquired();
        givenAccount(SOURCE, CIF, "ACTIVE");
        givenAccount(DEST, "C000000002", "ACTIVE");
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var transferId =
                service.create(command(SOURCE, DEST, null, "500000")).transfer().getTransferId();

        // then: Redis chi giu hash, OTP dang ro chi di qua OtpSender
        verify(otpStore).issue(transferId, OtpHash.of(transferId, OTP));
        verify(otpSender).send(transferId, OTP);
    }

    @Test
    void should_notSaveTransferAndReleaseKey_when_otpStoreUnavailable() {
        // given
        givenKeyAcquired();
        givenAccount(SOURCE, CIF, "ACTIVE");
        givenAccount(DEST, "C000000002", "ACTIVE");
        doThrow(new TechnicalException(TransferErrorCode.OTP_STORE_UNAVAILABLE, "down", null))
                .when(otpStore)
                .issue(anyString(), anyString());

        // when
        var thrown = catchThrowable(() -> service.create(command(SOURCE, DEST, null, "500000")));

        // then: chua co lenh nao, client gui lai cung key de tao lai
        assertThat(thrown).isInstanceOf(TechnicalException.class);
        verify(transferRepository, never()).saveAndFlush(any(Transfer.class));
        verify(otpSender, never()).send(anyString(), anyString());
        verify(idempotencyStore).release(CIF, KEY);
    }

    @Test
    void should_rejectWithSameAccountTransfer_when_destIsSourceAccount() {
        // given: khong can giu key hay goi account-service

        // when
        var thrown = catchThrowable(() -> service.create(command(SOURCE, SOURCE, null, "500000")));

        // then
        assertErrorCode(thrown, TransferErrorCode.SAME_ACCOUNT_TRANSFER);
        verify(idempotencyStore, never()).tryAcquire(anyString(), anyString(), anyString());
        verify(accountClient, never()).findAccount(anyString());
    }

    @Test
    void should_returnExistingTransfer_when_keyHeldAndTransferAlreadyCreatedWithSameContent() {
        // given
        var request = command(SOURCE, DEST, null, "500000");
        var existing = existingTransfer("500000");
        when(idempotencyStore.tryAcquire(anyString(), anyString(), anyString())).thenReturn(false);
        when(idempotencyStore.findFingerprint(CIF, KEY)).thenReturn(Optional.of(fingerprintOf(request)));
        when(transferRepository.findByCifAndIdempotencyKey(CIF, KEY)).thenReturn(Optional.of(existing));

        // when
        var result = service.create(request);

        // then
        assertThat(result.created()).isFalse();
        assertThat(result.transfer()).isSameAs(existing);
        verify(accountClient, never()).findAccount(anyString());
        verify(otpGenerator, never()).generate();
        verify(otpSender, never()).send(anyString(), anyString());
    }

    @Test
    void should_rejectWithIdempotencyKeyReused_when_keyHeldWithDifferentFingerprint() {
        // given
        when(idempotencyStore.tryAcquire(anyString(), anyString(), anyString())).thenReturn(false);
        when(idempotencyStore.findFingerprint(CIF, KEY))
                .thenReturn(Optional.of(fingerprintOf(command(SOURCE, DEST, null, "100000"))));

        // when
        var thrown = catchThrowable(() -> service.create(command(SOURCE, DEST, null, "500000")));

        // then
        assertErrorCode(thrown, TransferErrorCode.IDEMPOTENCY_KEY_REUSED);
        verify(transferRepository, never()).saveAndFlush(any(Transfer.class));
    }

    @Test
    void should_rejectWithIdempotencyKeyInProgress_when_keyHeldButTransferNotCreatedYet() {
        // given
        var request = command(SOURCE, DEST, null, "500000");
        when(idempotencyStore.tryAcquire(anyString(), anyString(), anyString())).thenReturn(false);
        when(idempotencyStore.findFingerprint(CIF, KEY)).thenReturn(Optional.of(fingerprintOf(request)));
        when(transferRepository.findByCifAndIdempotencyKey(CIF, KEY)).thenReturn(Optional.empty());

        // when
        var thrown = catchThrowable(() -> service.create(request));

        // then
        assertErrorCode(thrown, TransferErrorCode.IDEMPOTENCY_KEY_IN_PROGRESS);
    }

    @Test
    void should_returnExistingTransfer_when_redisLostKeyButDbHasSameTransfer() {
        // given
        var existing = existingTransfer("500000");
        givenKeyAcquired();
        when(transferRepository.findByCifAndIdempotencyKey(CIF, KEY)).thenReturn(Optional.of(existing));

        // when
        var result = service.create(command(SOURCE, DEST, null, "500000.00"));

        // then
        assertThat(result.created()).isFalse();
        assertThat(result.transfer()).isSameAs(existing);
        verify(transferRepository, never()).saveAndFlush(any(Transfer.class));
        verify(otpStore, never()).issue(anyString(), anyString());
        verify(otpSender, never()).send(anyString(), anyString());
    }

    @Test
    void should_rejectWithIdempotencyKeyReusedAndReleaseKey_when_redisLostKeyAndDbHasDifferentTransfer() {
        // given
        givenKeyAcquired();
        when(transferRepository.findByCifAndIdempotencyKey(CIF, KEY))
                .thenReturn(Optional.of(existingTransfer("100000")));

        // when
        var thrown = catchThrowable(() -> service.create(command(SOURCE, DEST, null, "500000")));

        // then
        assertErrorCode(thrown, TransferErrorCode.IDEMPOTENCY_KEY_REUSED);
        verify(idempotencyStore).release(CIF, KEY);
    }

    @Test
    void should_returnConcurrentTransfer_when_uniqueKeyViolatedOnSave() {
        // given
        var concurrent = existingTransfer("500000");
        givenKeyAcquired();
        givenAccount(SOURCE, CIF, "ACTIVE");
        givenAccount(DEST, "C000000002", "ACTIVE");
        when(transferRepository.findByCifAndIdempotencyKey(CIF, KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrent));
        when(transferRepository.saveAndFlush(any(Transfer.class)))
                .thenThrow(new DataIntegrityViolationException("UK_TRANSFER_CIF_IDEM_KEY"));

        // when
        var result = service.create(command(SOURCE, DEST, null, "500000"));

        // then
        assertThat(result.created()).isFalse();
        assertThat(result.transfer()).isSameAs(concurrent);
        verify(otpSender, never()).send(anyString(), anyString());
    }

    @Test
    void should_rejectWithSourceAccountAccessDeniedAndReleaseKey_when_sourceOwnedByOtherCustomer() {
        // given
        givenKeyAcquired();
        givenAccount(SOURCE, "C000000009", "ACTIVE");

        // when
        var thrown = catchThrowable(() -> service.create(command(SOURCE, DEST, null, "500000")));

        // then
        assertErrorCode(thrown, TransferErrorCode.SOURCE_ACCOUNT_ACCESS_DENIED);
        verify(idempotencyStore).release(CIF, KEY);
        verify(transferRepository, never()).saveAndFlush(any(Transfer.class));
    }

    @Test
    void should_rejectWithSourceAccountAccessDenied_when_sourceNotFound() {
        // given
        givenKeyAcquired();
        when(accountClient.findAccount(SOURCE)).thenReturn(Optional.empty());

        // when
        var thrown = catchThrowable(() -> service.create(command(SOURCE, DEST, null, "500000")));

        // then
        assertErrorCode(thrown, TransferErrorCode.SOURCE_ACCOUNT_ACCESS_DENIED);
    }

    @Test
    void should_rejectWithDestAccountNotFound_when_internalDestNotFound() {
        // given
        givenKeyAcquired();
        givenAccount(SOURCE, CIF, "ACTIVE");
        when(accountClient.findAccount(DEST)).thenReturn(Optional.empty());

        // when
        var thrown = catchThrowable(() -> service.create(command(SOURCE, DEST, null, "500000")));

        // then
        assertErrorCode(thrown, TransferErrorCode.DEST_ACCOUNT_NOT_FOUND);
        verify(idempotencyStore).release(CIF, KEY);
    }

    @Test
    void should_rejectWithDestAccountNotActive_when_internalDestLocked() {
        // given
        givenKeyAcquired();
        givenAccount(SOURCE, CIF, "ACTIVE");
        givenAccount(DEST, "C000000002", "LOCKED");

        // when
        var thrown = catchThrowable(() -> service.create(command(SOURCE, DEST, null, "500000")));

        // then
        assertErrorCode(thrown, TransferErrorCode.DEST_ACCOUNT_NOT_ACTIVE);
    }

    @Test
    void should_notLookUpDestAccount_when_interbankTransfer() {
        // given
        givenKeyAcquired();
        givenAccount(SOURCE, CIF, "ACTIVE");
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var result = service.create(command(SOURCE, DEST, NAPAS_BANK_CODE, "500000"));

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.transfer().getDestBankCode()).isEqualTo(NAPAS_BANK_CODE);
        verify(accountClient, never()).findAccount(DEST);
    }

    @Test
    void should_releaseKey_when_accountServiceUnavailable() {
        // given
        givenKeyAcquired();
        when(accountClient.findAccount(SOURCE))
                .thenThrow(new TechnicalException(TransferErrorCode.ACCOUNT_SERVICE_UNAVAILABLE, "down", null));

        // when
        var thrown = catchThrowable(() -> service.create(command(SOURCE, DEST, null, "500000")));

        // then
        assertThat(thrown).isInstanceOf(TechnicalException.class);
        verify(idempotencyStore).release(CIF, KEY);
    }

    private void givenKeyAcquired() {
        when(idempotencyStore.tryAcquire(anyString(), anyString(), anyString())).thenReturn(true);
        when(transferRepository.findByCifAndIdempotencyKey(CIF, KEY)).thenReturn(Optional.empty());
    }

    private void givenAccount(String accountNo, String cif, String status) {
        when(accountClient.findAccount(accountNo)).thenReturn(Optional.of(new AccountSnapshot(accountNo, cif, status)));
    }

    private static CreateTransferCommand command(String source, String dest, String destBankCode, String amount) {
        return new CreateTransferCommand(CIF, KEY, source, dest, destBankCode, new BigDecimal(amount), "Chuyen tien");
    }

    private static String fingerprintOf(CreateTransferCommand command) {
        return command.toNewTransfer(BigDecimal.ZERO).fingerprint();
    }

    private static Transfer existingTransfer(String amount) {
        var request = command(SOURCE, DEST, null, amount).toNewTransfer(BigDecimal.ZERO);
        return Transfer.initiate("7f1c2a9e-1d2b-4c3d-9e8f-0a1b2c3d4e5f", request, CLOCK);
    }

    private static void assertErrorCode(Throwable thrown, ErrorCode code) {
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }
}
