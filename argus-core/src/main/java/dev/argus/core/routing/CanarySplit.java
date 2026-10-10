package dev.argus.core.routing;

import java.util.Objects;

/** Sends {@code canaryPercent}% of a route's traffic to instances of {@code canaryVersion}. */
public record CanarySplit(String canaryVersion, int canaryPercent) {

    public CanarySplit {
        Objects.requireNonNull(canaryVersion, "canaryVersion");
        if (canaryPercent < 0 || canaryPercent > 100) {
            throw new IllegalArgumentException("canaryPercent must be 0..100");
        }
    }
}
