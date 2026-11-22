package vn.bank.common.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TopicsTest {

    @Test
    void should_appendDlqSuffix_when_toDlq() {
        // when
        var dlq = Topics.toDlq(Topics.TRANSFER_EVENTS);

        // then
        assertThat(dlq).isEqualTo("transfer.events.DLQ");
    }
}
