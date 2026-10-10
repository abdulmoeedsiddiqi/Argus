package dev.argus.core.model;

import java.time.Instant;
import java.util.Objects;

/** Stored metadata for an API key (the key itself is never kept). */
public record ApiKeyRecord(String apiKeyId, String keyHash, String consumerId, String planId,
                           boolean revoked, Instant expiresAt) {

    public ApiKeyRecord {
        Objects.requireNonNull(apiKeyId, "apiKeyId");
        Objects.requireNonNull(keyHash, "keyHash");
        Objects.requireNonNull(consumerId, "consumerId");
        Objects.requireNonNull(planId, "planId");
    }
}
