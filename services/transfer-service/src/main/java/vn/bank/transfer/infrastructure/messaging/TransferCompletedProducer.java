package vn.bank.transfer.infrastructure.messaging;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import vn.bank.common.events.EventEnvelope;
import vn.bank.common.events.Topics;
import vn.bank.common.events.transfer.TransferCompleted;
import vn.bank.common.outbox.OutboxWriter;
import vn.bank.common.web.GlobalExceptionHandler;
import vn.bank.transfer.domain.Transfer;

/**
 * Phat {@code TransferCompleted} len {@code transfer.events} qua outbox (BANK-046), key = transferId de giu thu tu event
 * theo lenh. Phai goi trong transaction chuyen lenh sang SUCCESS: event chi duoc gui khi transaction commit.
 */
@Component
@RequiredArgsConstructor
public class TransferCompletedProducer {

    static final String SOURCE = "transfer-service";
    static final String AGGREGATE_TYPE = "TRANSFER";

    private final OutboxWriter outboxWriter;
    private final TransferEventMapper transferEventMapper;
    private final Clock clock;

    public void send(Transfer transfer) {
        // Chua co tracing (BANK-070): lay traceId tu MDC neu co, khong co thi null
        var traceId = MDC.get(GlobalExceptionHandler.TRACE_ID_MDC_KEY);
        var event = EventEnvelope.of(
                TransferCompleted.EVENT_TYPE,
                SOURCE,
                traceId,
                transferEventMapper.toTransferCompleted(transfer),
                clock);
        outboxWriter.write(AGGREGATE_TYPE, transfer.getTransferId(), Topics.TRANSFER_EVENTS, event);
    }
}
