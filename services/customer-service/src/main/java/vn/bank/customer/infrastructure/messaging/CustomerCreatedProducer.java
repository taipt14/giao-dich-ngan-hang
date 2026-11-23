package vn.bank.customer.infrastructure.messaging;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import vn.bank.common.events.EventEnvelope;
import vn.bank.common.events.Topics;
import vn.bank.common.events.customer.CustomerCreated;
import vn.bank.common.web.GlobalExceptionHandler;
import vn.bank.customer.domain.Customer;

/** Gui {@code CustomerCreated} len {@code customer.events}, key = CIF de giu thu tu event theo khach hang. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerCreatedProducer {

    static final String SOURCE = "customer-service";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CustomerEventMapper customerEventMapper;
    private final Clock clock;

    public void send(Customer customer) {
        var cif = customer.getCif();
        // Chua co tracing (BANK-070): lay traceId tu MDC neu co, khong co thi null
        var traceId = MDC.get(GlobalExceptionHandler.TRACE_ID_MDC_KEY);
        var event = EventEnvelope.of(
                CustomerCreated.EVENT_TYPE, SOURCE, traceId, customerEventMapper.toCustomerCreated(customer), clock);
        kafkaTemplate.send(Topics.CUSTOMER_EVENTS, cif, event).whenComplete((result, ex) -> {
            if (ex != null) {
                // Chua co outbox (BANK-046): gui loi la mat event, can nguoi kiem tra va gui lai
                log.error("Failed to publish {} {} for customer {}", event.eventType(), event.eventId(), cif, ex);
            } else {
                log.info("Published {} {} for customer {}", event.eventType(), event.eventId(), cif);
            }
        });
    }
}
