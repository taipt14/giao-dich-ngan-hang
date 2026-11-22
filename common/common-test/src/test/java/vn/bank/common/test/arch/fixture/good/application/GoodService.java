package vn.bank.common.test.arch.fixture.good.application;

import java.math.BigDecimal;
import org.springframework.transaction.annotation.Transactional;
import vn.bank.common.test.arch.fixture.good.domain.GoodAccount;
import vn.bank.common.test.arch.fixture.good.infrastructure.persistence.GoodRepository;

@Transactional
public class GoodService {

    private final GoodRepository goodRepository;

    public GoodService(GoodRepository goodRepository) {
        this.goodRepository = goodRepository;
    }

    @Transactional(timeout = 10)
    public GoodAccount debit(String accountNo) {
        var account = goodRepository.getAccount(accountNo);
        account.debit(BigDecimal.ONE);
        return account;
    }
}
