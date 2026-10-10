package dev.argus.contracts;

import java.time.Instant;
import java.util.Objects;

/**
 * Published by the Admin API (compacted topic gateway.config, keyed by
 * entityType + ":" + entityId). A null payload is a tombstone meaning "deleted".
 */
public record ConfigChange(
        EntityType entityType,
        String entityId,
        long version,
        String payloadJson,
        Instant changedAt) {

    public enum EntityType { ROUTE, UPSTREAM, API_KEY, PLAN }

    public ConfigChange {
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(changedAt, "changedAt");
    }

    public String key() {
        return entityType + ":" + entityId;
    }

    public boolean isDeletion() {
        return payloadJson == null;
    }
}
