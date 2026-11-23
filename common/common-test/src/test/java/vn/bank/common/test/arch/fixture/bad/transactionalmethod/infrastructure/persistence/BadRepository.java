package vn.bank.common.test.arch.fixture.bad.transactionalmethod.infrastructure.persistence;

import org.springframework.transaction.annotation.Transactional;

public class BadRepository {

    @Transactional
    public void save() {}
}
