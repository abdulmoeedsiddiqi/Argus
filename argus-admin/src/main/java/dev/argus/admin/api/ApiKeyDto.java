package dev.argus.admin.api;

import java.time.Instant;

/**
 * What the Admin API shows about an existing API key. It has no field for the key or its
 * hash: after a key has been issued, only its id and display prefix can be seen (QR-11).
 */
public record ApiKeyDto(
        String id,
        String consumerId,
        String prefix,
        boolean revoked,
        Instant expiresAt,
        Instant createdAt) { }
