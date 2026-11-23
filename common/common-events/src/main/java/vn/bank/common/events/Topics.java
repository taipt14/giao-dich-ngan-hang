package vn.bank.common.events;

/** Ten topic Kafka theo muc 5 docs/kien-truc.md. La hang so compile-time de dung trong {@code @KafkaListener}. */
public final class Topics {

    public static final String CUSTOMER_EVENTS = "customer.events";
    public static final String ACCOUNT_EVENTS = "account.events";
    public static final String TRANSFER_EVENTS = "transfer.events";
    public static final String NAPAS_REQUESTS = "napas.requests";
    public static final String NAPAS_RESPONSES = "napas.responses";
    public static final String FRAUD_ALERTS = "fraud.alerts";

    /** Hau to topic chua message loi sau khi het retry: {@code <topic>.DLQ}. */
    public static final String DLQ_SUFFIX = ".DLQ";

    private Topics() {}

    public static String toDlq(String topic) {
        return topic + DLQ_SUFFIX;
    }
}
