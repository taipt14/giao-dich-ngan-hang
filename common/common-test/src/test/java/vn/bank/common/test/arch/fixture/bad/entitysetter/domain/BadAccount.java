package vn.bank.common.test.arch.fixture.bad.entitysetter.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;

// Tuong duong bytecode Lombok @Setter/@Data sinh ra
@Entity
public class BadAccount {

    @Id
    private String accountNo;

    private BigDecimal balance;

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
