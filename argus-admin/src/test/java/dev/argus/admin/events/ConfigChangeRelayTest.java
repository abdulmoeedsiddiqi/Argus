package dev.argus.admin.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.argus.contracts.ConfigChange;
import dev.argus.contracts.ConfigChange.EntityType;
import dev.argus.contracts.PlanConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * FR-12: a change is handed to the publisher only after the database transaction has
 * committed. Tested without a database: the transaction is simulated with Spring's own
 * synchronization hooks, which is exactly what a real commit or rollback calls.
 */
class ConfigChangeRelayTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    private static final PlanConfig PLAN = new PlanConfig("free", "Free", 60, 60000, 10000);

    private final List<ConfigChange> published = new ArrayList<>();
    private final ConfigChangeRelay relay = new ConfigChangeRelay(
            published::add, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("FR-12: inside a transaction nothing is published until the commit")
    void publishesOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        relay.afterCommit(EntityType.PLAN, "free", 7, PLAN);
        assertThat(published).as("before the commit").isEmpty();

        commit();
        assertThat(published).hasSize(1);
        ConfigChange change = published.get(0);
        assertThat(change.key()).isEqualTo("PLAN:free");
        assertThat(change.version()).isEqualTo(7);
        assertThat(change.changedAt()).isEqualTo(NOW);
        assertThat(change.payloadJson()).contains("\"rateCapacity\":60");
    }

    @Test
    @DisplayName("FR-12: a transaction that rolls back publishes nothing")
    void rollbackPublishesNothing() {
        TransactionSynchronizationManager.initSynchronization();
        relay.afterCommit(EntityType.PLAN, "free", 7, PLAN);

        rollback();

        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("FR-12: several changes of one transaction are published in their order")
    void keepsTheOrderOfChanges() {
        TransactionSynchronizationManager.initSynchronization();
        relay.afterCommit(EntityType.API_KEY, "key-new", 8, PLAN);
        relay.afterCommit(EntityType.API_KEY, "key-old", 9, PLAN);

        commit();

        assertThat(published).extracting(ConfigChange::key)
                .containsExactly("API_KEY:key-new", "API_KEY:key-old");
    }

    @Test
    @DisplayName("FR-12: a deletion is published as a change without a payload (tombstone)")
    void deletionHasNoPayload() {
        relay.afterCommit(EntityType.ROUTE, "orders", 3, null);

        assertThat(published).hasSize(1);
        assertThat(published.get(0).isDeletion()).isTrue();
    }

    @Test
    @DisplayName("a failed publish is reported as 'saved but not propagated', not swallowed")
    void publishFailureIsReported() {
        ConfigChangeRelay failing = new ConfigChangeRelay(change -> {
            throw new ConfigNotPropagatedException("Kafka is not reachable", null);
        }, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> failing.afterCommit(EntityType.PLAN, "free", 7, PLAN))
                .isInstanceOf(ConfigNotPropagatedException.class);
    }

    private static void commit() {
        for (TransactionSynchronization hook : TransactionSynchronizationManager.getSynchronizations()) {
            hook.afterCommit();
        }
    }

    private static void rollback() {
        for (TransactionSynchronization hook : TransactionSynchronizationManager.getSynchronizations()) {
            hook.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
    }
}
