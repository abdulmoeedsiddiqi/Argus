package dev.argus.admin.events;

import dev.argus.contracts.ConfigChange;

/**
 * Port: sends one configuration change to every gateway instance. The Admin API only knows
 * this interface; Kafka is hidden behind {@link KafkaConfigChangePublisher} (Adapter).
 */
@FunctionalInterface
public interface ConfigChangePublisher {

    /**
     * Returns only when the change is safely handed over.
     *
     * @throws ConfigNotPropagatedException if it could not be sent
     */
    void publish(ConfigChange change);
}
