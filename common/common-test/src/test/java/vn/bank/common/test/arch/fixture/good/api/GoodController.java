package vn.bank.common.test.arch.fixture.good.api;

import vn.bank.common.test.arch.fixture.good.application.GoodService;
import vn.bank.common.test.arch.fixture.good.domain.GoodAccount;

public class GoodController {

    private final GoodService goodService;

    public GoodController(GoodService goodService) {
        this.goodService = goodService;
    }

    public GoodAccount debit(String accountNo) {
        return goodService.debit(accountNo);
    }
}
