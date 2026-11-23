package vn.bank.common.test.arch.fixture.good.infrastructure.messaging;

import vn.bank.common.test.arch.fixture.good.application.GoodService;

public class GoodConsumer {

    private final GoodService goodService;

    public GoodConsumer(GoodService goodService) {
        this.goodService = goodService;
    }

    public void onMessage(String accountNo) {
        goodService.debit(accountNo);
    }
}
