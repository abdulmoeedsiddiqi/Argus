package dev.argus.gateway.auth;

import dev.argus.core.auth.ApiKeyStore;
import dev.argus.core.model.ApiKeyRecord;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The gateway's in-memory copy of the API key records, behind the core's {@link ApiKeyStore}
 * port. It holds hashes and metadata only, never a plaintext key.
 *
 * <p>For now it is filled from configuration at start-up. From module M5 on, the same
 * {@link #replaceAll} is fed by the configuration topic, so a revoked key stops working on
 * every instance.
 *
 * <p>Thread-safety: an immutable map behind an {@link AtomicReference}. Request threads read
 * the current map without locking; an update builds a complete new map and swaps it in.
 */
public final class ApiKeyCache implements ApiKeyStore {

    private final AtomicReference<Map<String, ApiKeyRecord>> byHash = new AtomicReference<>(Map.of());

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
}
