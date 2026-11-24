package vn.bank.account.infrastructure.messaging;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import vn.bank.account.domain.Account;
import vn.bank.common.events.EventEnvelope;
import vn.bank.common.events.Topics;
import vn.bank.common.events.account.AccountOpened;
import vn.bank.common.outbox.OutboxWriter;
import vn.bank.common.web.GlobalExceptionHandler;

/**
 * Phat {@code AccountOpened} len {@code account.events} qua outbox (BANK-046), key = so tai khoan de giu thu tu event
 * theo tai khoan. Phai goi trong transaction mo tai khoan: event chi duoc gui khi transaction commit.
 */
@Component
@RequiredArgsConstructor
public class AccountOpenedProducer {

    static final String SOURCE = "account-service";
    static final String AGGREGATE_TYPE = "ACCOUNT";

    private final OutboxWriter outboxWriter;
    private final AccountEventMapper accountEventMapper;
    private final Clock clock;

    public void send(Account account) {
        // Chua co tracing (BANK-070): lay traceId tu MDC neu co, khong co thi null
        var traceId = MDC.get(GlobalExceptionHandler.TRACE_ID_MDC_KEY);
        var event = EventEnvelope.of(
                AccountOpened.EVENT_TYPE, SOURCE, traceId, accountEventMapper.toAccountOpened(account), clock);
        outboxWriter.write(AGGREGATE_TYPE, account.getAccountNo(), Topics.ACCOUNT_EVENTS, event);
    }
}
