package dev.argus.admin.api;

import java.time.Instant;

/**
 * The answer to "issue" and "rotate": the only time the full key is ever shown. The server
 * keeps just its hash, so a lost key cannot be recovered, only replaced.
 */
public record IssuedKeyDto(
        String id,
        String key,
        String consumerId,
        String prefix,
        boolean revoked,
        Instant expiresAt,
        Instant createdAt) {

    /** Never prints the key, so it cannot leak through a log line. */
    @Override
    public String toString() {
        return "IssuedKeyDto[id=" + id + ", key=<hidden>, prefix=" + prefix + "]";
    }
}
