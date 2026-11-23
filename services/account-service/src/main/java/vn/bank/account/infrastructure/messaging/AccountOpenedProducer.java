package vn.bank.account.infrastructure.messaging;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import vn.bank.account.domain.Account;
import vn.bank.common.events.EventEnvelope;
import vn.bank.common.events.Topics;
import vn.bank.common.events.account.AccountOpened;
import vn.bank.common.web.GlobalExceptionHandler;

/** Gui {@code AccountOpened} len {@code account.events}, key = so tai khoan de giu thu tu event theo tai khoan. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountOpenedProducer {

    static final String SOURCE = "account-service";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AccountEventMapper accountEventMapper;
    private final Clock clock;

    public void send(Account account) {
        var accountNo = account.getAccountNo();
        var maskedAccountNo = account.getMaskedAccountNo();
        // Chua co tracing (BANK-070): lay traceId tu MDC neu co, khong co thi null
        var traceId = MDC.get(GlobalExceptionHandler.TRACE_ID_MDC_KEY);
        var event = EventEnvelope.of(
                AccountOpened.EVENT_TYPE, SOURCE, traceId, accountEventMapper.toAccountOpened(account), clock);
        kafkaTemplate.send(Topics.ACCOUNT_EVENTS, accountNo, event).whenComplete((result, ex) -> {
            if (ex != null) {
                // Chua co outbox (BANK-046): gui loi la mat event, can nguoi kiem tra va gui lai
                log.error(
                        "Failed to publish {} {} for account {}",
                        event.eventType(),
                        event.eventId(),
                        maskedAccountNo,
                        ex);
            } else {
                log.info("Published {} {} for account {}", event.eventType(), event.eventId(), maskedAccountNo);
            }
        });
    }
}
