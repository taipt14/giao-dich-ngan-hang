package vn.bank.common.test.arch.fixture.good.infrastructure.persistence;

import vn.bank.common.test.arch.fixture.good.domain.GoodAccount;

public class GoodRepository {

    public GoodAccount getAccount(String accountNo) {
        return GoodAccount.open(accountNo);
    }
}
