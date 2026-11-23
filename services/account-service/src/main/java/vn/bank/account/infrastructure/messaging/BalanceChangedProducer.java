package vn.bank.account.infrastructure.messaging;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import vn.bank.account.domain.BalanceChange;
import vn.bank.common.events.EventEnvelope;
import vn.bank.common.events.Topics;
import vn.bank.common.events.account.BalanceChanged;
import vn.bank.common.web.GlobalExceptionHandler;

/** Gui {@code BalanceChanged} len {@code account.events}, key = so tai khoan de giu thu tu event theo tai khoan. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BalanceChangedProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AccountEventMapper accountEventMapper;
    private final Clock clock;

    public void send(BalanceChange change) {
        var maskedAccountNo = change.maskedAccountNo();
        // Chua co tracing (BANK-070): lay traceId tu MDC neu co, khong co thi null
        var traceId = MDC.get(GlobalExceptionHandler.TRACE_ID_MDC_KEY);
        var event = EventEnvelope.of(
                BalanceChanged.EVENT_TYPE,
                AccountOpenedProducer.SOURCE,
                traceId,
                accountEventMapper.toBalanceChanged(change),
                clock);
        kafkaTemplate.send(Topics.ACCOUNT_EVENTS, change.accountNo(), event).whenComplete((result, ex) -> {
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
