package vn.bank.customer.infrastructure.messaging;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import vn.bank.common.events.EventEnvelope;
import vn.bank.common.events.Topics;
import vn.bank.common.events.customer.CustomerStatusChanged;
import vn.bank.common.outbox.OutboxWriter;
import vn.bank.common.web.GlobalExceptionHandler;
import vn.bank.customer.domain.CustomerStatusChange;

/**
 * Phat {@code CustomerStatusChanged} len {@code customer.events} qua outbox (BANK-046), key = CIF de giu thu tu event
 * theo khach hang. Phai goi trong transaction doi trang thai: event chi duoc gui khi transaction commit.
 */
@Component
@RequiredArgsConstructor
public class CustomerStatusChangedProducer {

    private final OutboxWriter outboxWriter;
    private final CustomerEventMapper customerEventMapper;
    private final Clock clock;

    public void send(CustomerStatusChange change) {
        // Chua co tracing (BANK-070): lay traceId tu MDC neu co, khong co thi null
        var traceId = MDC.get(GlobalExceptionHandler.TRACE_ID_MDC_KEY);
        var event = EventEnvelope.of(
                CustomerStatusChanged.EVENT_TYPE,
                CustomerCreatedProducer.SOURCE,
                traceId,
                customerEventMapper.toCustomerStatusChanged(change),
                clock);
        outboxWriter.write(CustomerCreatedProducer.AGGREGATE_TYPE, change.cif(), Topics.CUSTOMER_EVENTS, event);
    }
}
