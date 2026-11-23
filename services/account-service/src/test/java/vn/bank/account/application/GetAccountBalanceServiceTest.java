package vn.bank.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountBalance;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.account.domain.AccountHolder;
import vn.bank.account.domain.AccountType;
import vn.bank.account.infrastructure.cache.BalanceCache;
import vn.bank.account.infrastructure.persistence.AccountRepository;
import vn.bank.common.error.BusinessException;

class GetAccountBalanceServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC);
    private static final String CIF = "C000000001";
    private static final String OTHER_CIF = "C000000002";
    private static final String ACCOUNT_NO = "100000000001";
    private static final AccountBalance CACHED =
            new AccountBalance(ACCOUNT_NO, CIF, "VND", new BigDecimal("1500000.00"), new BigDecimal("1200000.00"));

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final BalanceCache balanceCache = mock(BalanceCache.class);
    private final GetAccountBalanceService service = new GetAccountBalanceService(accountRepository, balanceCache);

    @Test
    void should_notQueryDb_when_balanceCached() {
        // given
        when(balanceCache.find(ACCOUNT_NO)).thenReturn(Optional.of(CACHED));

        // when
        var balance = service.getBalance(CIF, ACCOUNT_NO);

        // then
        assertThat(balance).isEqualTo(CACHED);
        verifyNoInteractions(accountRepository);
        verify(balanceCache, never()).put(any());
    }

    @Test
    void should_readDbAndCacheBalance_when_cacheMiss() {
        // given
        when(balanceCache.find(ACCOUNT_NO)).thenReturn(Optional.empty());
        when(accountRepository.findById(ACCOUNT_NO)).thenReturn(Optional.of(accountOf(CIF)));

        // when
        var balance = service.getBalance(CIF, ACCOUNT_NO);

        // then
        assertThat(balance.accountNo()).isEqualTo(ACCOUNT_NO);
        assertThat(balance.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.availableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(balanceCache).put(balance);
    }

    @Test
    void should_throwAccountAccessDenied_when_cachedAccountBelongsToOtherCustomer() {
        // given
        when(balanceCache.find(ACCOUNT_NO)).thenReturn(Optional.of(CACHED));

        // when
        var thrown = catchThrowable(() -> service.getBalance(OTHER_CIF, ACCOUNT_NO));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_ACCESS_DENIED);
    }

    @Test
    void should_throwAccountAccessDenied_when_accountFromDbBelongsToOtherCustomer() {
        // given
        when(balanceCache.find(ACCOUNT_NO)).thenReturn(Optional.empty());
        when(accountRepository.findById(ACCOUNT_NO)).thenReturn(Optional.of(accountOf(CIF)));

        // when
        var thrown = catchThrowable(() -> service.getBalance(OTHER_CIF, ACCOUNT_NO));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_ACCESS_DENIED);
    }

    @Test
    void should_throwAccountNotFound_when_accountDoesNotExist() {
        // given
        when(balanceCache.find(ACCOUNT_NO)).thenReturn(Optional.empty());
        when(accountRepository.findById(ACCOUNT_NO)).thenReturn(Optional.empty());

        // when
        var thrown = catchThrowable(() -> service.getBalance(CIF, ACCOUNT_NO));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
        verify(balanceCache, never()).put(any());
    }

    private static Account accountOf(String cif) {
        return Account.open(ACCOUNT_NO, new AccountHolder(cif, "ACTIVE"), AccountType.CURRENT, 0, CLOCK);
    }
}
