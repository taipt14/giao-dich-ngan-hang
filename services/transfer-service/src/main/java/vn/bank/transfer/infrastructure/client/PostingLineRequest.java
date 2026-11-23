package vn.bank.transfer.infrastructure.client;

import java.math.BigDecimal;
import vn.bank.transfer.domain.PostingDirection;

record PostingLineRequest(String accountNo, PostingDirection direction, BigDecimal amount) {}
