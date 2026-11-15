package dev.argus.core.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Stored metadata for an API key. The key itself is never kept: only its SHA-256 hash, plus a
 * short {@code displayPrefix} (the first characters of the key) so that people can tell keys
 * apart in lists and logs without seeing a usable key.
 *
 * <p>{@code expiresAt} may be null, which means the key does not expire.
 */
public record ApiKeyRecord(String apiKeyId, String keyHash, String displayPrefix, String consumerId,
                           String planId, boolean revoked, Instant expiresAt) {

    public ApiKeyRecord {
        Objects.requireNonNull(apiKeyId, "apiKeyId");
        Objects.requireNonNull(keyHash, "keyHash");
        Objects.requireNonNull(displayPrefix, "displayPrefix");
        Objects.requireNonNull(consumerId, "consumerId");
        Objects.requireNonNull(planId, "planId");
    }
}
