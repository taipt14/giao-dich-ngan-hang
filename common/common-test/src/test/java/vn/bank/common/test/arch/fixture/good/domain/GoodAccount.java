package vn.bank.common.test.arch.fixture.good.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;

@Entity
public class GoodAccount {

    @Id
    private String accountNo;

    private BigDecimal balance;

    protected GoodAccount() {}

    public static GoodAccount open(String accountNo) {
        var account = new GoodAccount();
        account.accountNo = accountNo;
        account.balance = BigDecimal.ZERO;
        return account;
    }

    public void debit(BigDecimal amount) {
        balance = balance.subtract(amount);
    }

    public String getAccountNo() {
        return accountNo;
    }
}
