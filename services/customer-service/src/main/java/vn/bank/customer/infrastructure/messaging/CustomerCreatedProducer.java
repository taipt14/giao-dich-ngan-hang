package vn.bank.customer.infrastructure.messaging;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import vn.bank.common.events.EventEnvelope;
import vn.bank.common.events.Topics;
import vn.bank.common.events.customer.CustomerCreated;
import vn.bank.common.outbox.OutboxWriter;
import vn.bank.common.web.GlobalExceptionHandler;
import vn.bank.customer.domain.Customer;

/**
 * Phat {@code CustomerCreated} len {@code customer.events} qua outbox (BANK-046), key = CIF de giu thu tu event theo
 * khach hang. Phai goi trong transaction luu khach hang: event chi duoc gui khi transaction commit.
 */
@Component
@RequiredArgsConstructor
public class CustomerCreatedProducer {

    static final String SOURCE = "customer-service";
    static final String AGGREGATE_TYPE = "CUSTOMER";

    private final OutboxWriter outboxWriter;
    private final CustomerEventMapper customerEventMapper;
    private final Clock clock;

    public void send(Customer customer) {
        // Chua co tracing (BANK-070): lay traceId tu MDC neu co, khong co thi null
        var traceId = MDC.get(GlobalExceptionHandler.TRACE_ID_MDC_KEY);
        var event = EventEnvelope.of(
                CustomerCreated.EVENT_TYPE, SOURCE, traceId, customerEventMapper.toCustomerCreated(customer), clock);
        outboxWriter.write(AGGREGATE_TYPE, customer.getCif(), Topics.CUSTOMER_EVENTS, event);
    }
}
