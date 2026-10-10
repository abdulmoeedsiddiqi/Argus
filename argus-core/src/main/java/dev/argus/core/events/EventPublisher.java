package dev.argus.core.events;

import dev.argus.contracts.RequestEvent;

/**
 * Port for publishing request events. The core depends only on this interface;
 * KafkaEventPublisher (adapter) and SpillingEventPublisher (decorator) live in the gateway
 * module. publish() must never block the request path or throw to the caller.
 */
public interface EventPublisher {

    void publish(RequestEvent event);
}
