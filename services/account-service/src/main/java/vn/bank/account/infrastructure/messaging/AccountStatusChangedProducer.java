package vn.bank.account.infrastructure.messaging;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import vn.bank.account.domain.AccountStatusChange;
import vn.bank.common.events.EventEnvelope;
import vn.bank.common.events.Topics;
import vn.bank.common.events.account.AccountStatusChanged;
import vn.bank.common.outbox.OutboxWriter;
import vn.bank.common.web.GlobalExceptionHandler;

/**
 * Phat {@code AccountStatusChanged} len {@code account.events} qua outbox (BANK-046), key = so tai khoan de giu thu tu
 * event theo tai khoan. Phai goi trong transaction doi trang thai: event chi duoc gui khi transaction commit.
 */
@Component
@RequiredArgsConstructor
public class AccountStatusChangedProducer {

    private final OutboxWriter outboxWriter;
    private final AccountEventMapper accountEventMapper;
    private final Clock clock;

    public void send(AccountStatusChange change) {
        // Chua co tracing (BANK-070): lay traceId tu MDC neu co, khong co thi null
        var traceId = MDC.get(GlobalExceptionHandler.TRACE_ID_MDC_KEY);
        var event = EventEnvelope.of(
                AccountStatusChanged.EVENT_TYPE,
                AccountOpenedProducer.SOURCE,
                traceId,
                accountEventMapper.toAccountStatusChanged(change),
                clock);
        outboxWriter.write(AccountOpenedProducer.AGGREGATE_TYPE, change.accountNo(), Topics.ACCOUNT_EVENTS, event);
    }
}
