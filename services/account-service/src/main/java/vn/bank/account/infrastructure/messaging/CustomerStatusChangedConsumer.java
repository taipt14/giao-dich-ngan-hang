package vn.bank.account.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import vn.bank.account.application.LockCustomerAccountsService;
import vn.bank.common.events.EventEnvelope;
import vn.bank.common.events.EventJson;
import vn.bank.common.events.Topics;
import vn.bank.common.events.customer.CustomerStatusChanged;

/**
 * Nhan {@code CustomerStatusChanged} tu {@code customer.events} va chuyen cho {@link LockCustomerAccountsService}
 * (kiem tra trung message nam trong transaction cua service). Topic con chua event khac (CustomerCreated) nen payload
 * doc dang cay JSON, chi chuyen thanh record khi dung loai event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CustomerStatusChangedConsumer {

    static final String GROUP_ID = "account-service";

    private final LockCustomerAccountsService lockCustomerAccountsService;

    @KafkaListener(topics = Topics.CUSTOMER_EVENTS, groupId = GROUP_ID)
    void onMessage(EventEnvelope<JsonNode> event) {
        if (!CustomerStatusChanged.EVENT_TYPE.equals(event.eventType())) {
            log.debug("Skip {} {} on {}", event.eventType(), event.eventId(), Topics.CUSTOMER_EVENTS);
            return;
        }
        var payload = EventJson.mapper().treeToValue(event.payload(), CustomerStatusChanged.class);
        lockCustomerAccountsService.handle(event.eventId(), payload);
    }
}
