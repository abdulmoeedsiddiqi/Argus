package dev.argus.core.model;

import java.time.Duration;
import java.util.Objects;

/** Token-bucket parameters: up to {@code capacity} requests, refilled every {@code refillPeriod}. */
public record RateLimitPolicy(int capacity, Duration refillPeriod) {

    public RateLimitPolicy {
        Objects.requireNonNull(refillPeriod, "refillPeriod");
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        if (refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("refillPeriod must be positive");
        }
    }
}
