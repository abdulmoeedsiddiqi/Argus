package dev.argus.core.ratelimit;

import java.util.Objects;

/** Identifies one bucket: a consumer's limit on one route. */
public record RateLimitKey(String consumerId, String routeId) {

    public RateLimitKey {
        Objects.requireNonNull(consumerId, "consumerId");
        Objects.requireNonNull(routeId, "routeId");
    }

    public String asStorageKey() {
        return "rl:" + consumerId + ":" + routeId;
    }
}
