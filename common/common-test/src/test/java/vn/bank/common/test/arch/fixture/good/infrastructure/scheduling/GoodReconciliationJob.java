package vn.bank.common.test.arch.fixture.good.infrastructure.scheduling;

import vn.bank.common.test.arch.fixture.good.application.GoodService;

public class GoodReconciliationJob {

    private final GoodService goodService;

    public GoodReconciliationJob(GoodService goodService) {
        this.goodService = goodService;
    }

    public void run(String accountNo) {
        goodService.debit(accountNo);
    }
}
