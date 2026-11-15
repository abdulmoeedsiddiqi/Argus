package dev.argus.contracts;

import java.time.Instant;

/**
 * Payload of a {@link ConfigChange} of type API_KEY. It carries the SHA-256 hash of the key,
 * never the key itself, so the configuration topic holds no usable credential.
 *
 * @param expiresAt when the key stops working, or null if it never expires
 */
public record ApiKeyConfig(
        String id,
        String keyHash,
        String displayPrefix,
        String consumerId,
        String planId,
        boolean revoked,
        Instant expiresAt) { }
