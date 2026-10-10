package dev.argus.contracts;

/** Topic names shared by every service, so producers and consumers cannot drift apart. */
public final class Topics {

    /** Normal topic, 7-day retention, key = apiKeyId. */
    public static final String REQUEST_EVENTS = "gateway.request-events";

    /** Compacted topic, key = apiKeyId. */
    public static final String ENFORCEMENT = "gateway.enforcement";

    /** Compacted topic, key = entityType:entityId. */
    public static final String CONFIG = "gateway.config";

    /** Dead-letter topic for request events that repeatedly fail processing. */
    public static final String REQUEST_EVENTS_DLQ = "gateway.request-events.dlq";

    private Topics() {
    }
}
