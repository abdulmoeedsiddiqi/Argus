package dev.argus.contracts;

import java.time.Instant;
import java.util.Objects;

/**
 * Published by the Metering and Abuse Detection services (compacted topic
 * gateway.enforcement, keyed by apiKeyId). The latest decision per key wins.
 */
public record EnforcementDecision(
        String apiKeyId,
        Action action,
        String reason,
        String decidedBy,
        Instant decidedAt,
        Instant expiresAt) {

    public enum Action { BLOCK, QUOTA_EXCEEDED, CLEAR }

    public EnforcementDecision {
        Objects.requireNonNull(apiKeyId, "apiKeyId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(decidedAt, "decidedAt");
    }
}
