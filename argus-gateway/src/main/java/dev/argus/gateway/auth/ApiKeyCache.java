package dev.argus.gateway.auth;

import dev.argus.contracts.ApiKeyConfig;
import dev.argus.contracts.ConfigChange;
import dev.argus.contracts.ConfigChange.EntityType;
import dev.argus.core.auth.ApiKeyStore;
import dev.argus.core.events.ConfigurationListener;
import dev.argus.core.model.ApiKeyRecord;
import dev.argus.gateway.events.ConfigChangeReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The gateway's in-memory copy of the API key records, behind the core's {@link ApiKeyStore}
 * port. It holds hashes and metadata only, never a plaintext key.
 *
 * <p>It is filled in one of two ways: from a configuration file at start-up
 * ({@link #replaceAll}), or, as an Observer of the configuration topic ({@link #onChange}),
 * from the changes that the Admin API publishes. The second way is what makes a revoked key
 * stop working on every instance (QR-12).
 *
 * <p>Thread-safety: an immutable map behind an {@link AtomicReference}. Request threads read
 * the current map without locking; an update builds a complete new map and swaps it in.
 */
public final class ApiKeyCache implements ApiKeyStore, ConfigurationListener {

    private static final Logger LOG = LoggerFactory.getLogger(ApiKeyCache.class);

    private final AtomicReference<Map<String, ApiKeyRecord>> byHash = new AtomicReference<>(Map.of());
    private final ConfigChangeReader reader = new ConfigChangeReader();

    @Override
    public Optional<ApiKeyRecord> findByHash(String keyHash) {
        return Optional.ofNullable(byHash.get().get(keyHash));
    }

    /** Replaces all records. The new set is validated before it becomes visible. */
    public void replaceAll(Collection<ApiKeyRecord> records) {
        Objects.requireNonNull(records, "records");
        Map<String, ApiKeyRecord> next = new HashMap<>();
        for (ApiKeyRecord record : records) {
            if (next.put(record.keyHash(), record) != null) {
                throw new IllegalArgumentException(
                        "two API key records share one hash (key id " + record.apiKeyId() + ")");
            }
        }
        byHash.set(Map.copyOf(next));
    }

    /** Applies one API_KEY change: the key is added, replaced (for example revoked) or removed. */
    @Override
    public void onChange(ConfigChange change) {
        if (change.entityType() != EntityType.API_KEY) {
            return;
        }
        try {
            ApiKeyRecord updated = change.isDeletion()
                    ? null
                    : toRecord(reader.payload(change, ApiKeyConfig.class));
            byHash.updateAndGet(current -> withChange(current, change.entityId(), updated));
        } catch (RuntimeException invalid) {
            // A bad record must not stop the gateway: keep the known keys and say so.
            LOG.warn("ignored configuration change {}: {}", change.key(), invalid.getMessage());
        }
    }

    /** A new map: the old one without the key of this id, plus its new record (if any). */
    private static Map<String, ApiKeyRecord> withChange(
            Map<String, ApiKeyRecord> current, String apiKeyId, ApiKeyRecord updated) {
        Map<String, ApiKeyRecord> next = new HashMap<>(current);
        next.values().removeIf(existing -> existing.apiKeyId().equals(apiKeyId));
        if (updated != null) {
            next.put(updated.keyHash(), updated);
        }
        return Map.copyOf(next);
    }

    private static ApiKeyRecord toRecord(ApiKeyConfig config) {
        return new ApiKeyRecord(
                config.id(), config.keyHash(), config.displayPrefix(), config.consumerId(),
                config.planId(), config.revoked(), config.expiresAt());
    }
}
