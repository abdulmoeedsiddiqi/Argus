package dev.argus.admin.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.argus.contracts.ConfigChange;
import dev.argus.contracts.ConfigChange.EntityType;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes a change only after the database transaction that made it has committed (FR-12).
 *
 * <p>Why after the commit: if we published first and the transaction then failed, the gateways
 * would apply a change that the database never stored. A service therefore calls
 * {@link #afterCommit} while its transaction is still open; the change is remembered and sent
 * by Spring's "after commit" hook. If the transaction rolls back, the hook never runs and
 * nothing is sent.
 */
@Component
public class ConfigChangeRelay {

    private final ConfigChangePublisher publisher;
    private final ObjectMapper json;
    private final Clock clock;

    public ConfigChangeRelay(ConfigChangePublisher publisher, ObjectMapper json, Clock clock) {
        this.publisher = publisher;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Schedules one change for publishing.
     *
     * @param version  a number that grows with every change (the audit record number)
     * @param payload  the entity as a payload record (RouteConfig, ...), or null if it was deleted
     */
    public void afterCommit(EntityType type, String entityId, long version, Object payload) {
        ConfigChange change = new ConfigChange(
                type, entityId, version, payload == null ? null : toJson(payload), clock.instant());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // No transaction is open, so there is no commit to wait for.
            publisher.publish(change);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publish(change);
            }
        });
    }

    private String toJson(Object payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a configuration payload could not be written as JSON", e);
        }
    }
}
