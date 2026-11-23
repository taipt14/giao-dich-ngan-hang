package vn.bank.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import vn.bank.account.domain.Account;
import vn.bank.account.domain.AccountErrorCode;
import vn.bank.account.domain.AccountHolder;
import vn.bank.account.domain.AccountType;
import vn.bank.account.infrastructure.client.CustomerClient;
import vn.bank.common.error.BusinessException;

class OpenAccountServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC);
    private static final String CIF = "C000000001";

    private final CustomerClient customerClient = mock(CustomerClient.class);
    private final CreateAccountService createAccountService = mock(CreateAccountService.class);
    private final OpenAccountService openAccountService = new OpenAccountService(customerClient, createAccountService);

    @Test
    void should_createAccountForHolderFromCustomerService_when_customerFound() {
        // given
        var holder = new AccountHolder(CIF, "ACTIVE");
        var created = Account.open("100000000001", holder, AccountType.CURRENT, 0, CLOCK);
        when(customerClient.getAccountHolder(CIF)).thenReturn(holder);
        when(createAccountService.create(holder, AccountType.CURRENT)).thenReturn(created);

        // when
        var account = openAccountService.open(CIF, AccountType.CURRENT);

        // then
        assertThat(account).isSameAs(created);
    }

    @Test
    void should_notCreateAccount_when_customerNotFound() {
        // given
        when(customerClient.getAccountHolder(CIF))
                .thenThrow(new BusinessException(AccountErrorCode.CUSTOMER_NOT_FOUND, "Customer not found: " + CIF));

        // when
        var thrown = catchThrowable(() -> openAccountService.open(CIF, AccountType.CURRENT));

        // then
        assertThat(thrown)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.CUSTOMER_NOT_FOUND);
        verify(createAccountService, never()).create(any(), any());
    }
}
