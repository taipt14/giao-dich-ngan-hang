package vn.bank.common.events;

import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJacksonJsonMessageConverter;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cau hinh JSON (Jackson 3) dung chung cho event Kafka. Tach rieng voi JsonMapper cua REST de doi cau hinh web khong
 * lam doi format event giua cac service.
 */
public final class EventJson {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            // Instant dang ISO-8601 UTC, khong dang so
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            // Tien ghi dang thuong (1000000.00), khong dang khoa hoc
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            // Payload khong khai bao kieu (Map, JsonNode) van giu so thap phan la BigDecimal, khong thanh double
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            // Tuong thich nguoc: producer them field moi thi consumer cu van doc duoc
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private EventJson() {}

    public static JsonMapper mapper() {
        return MAPPER;
    }

    /**
     * Serializer cho value cua producer. Khong ghi header kieu class: consumer suy ra kieu tu tham so method
     * {@code @KafkaListener}, khong phu thuoc ten class cua service gui.
     */
    public static <T> JacksonJsonSerializer<T> serializer() {
        return new JacksonJsonSerializer<T>(MAPPER).noTypeInfo();
    }

    /** Chuyen value String cua record thanh kieu tham so {@code @KafkaListener}, vi du {@code EventEnvelope<X>}. */
    public static RecordMessageConverter messageConverter() {
        return new StringJacksonJsonMessageConverter(MAPPER);
    }
}
