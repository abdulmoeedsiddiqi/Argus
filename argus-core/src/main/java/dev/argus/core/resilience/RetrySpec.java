package dev.argus.core.resilience;

import java.time.Duration;
import java.util.Objects;

/** Retry configuration for a route: bounded attempts with exponential backoff. */
public record RetrySpec(int maxAttempts, Duration initialBackoff, double multiplier) {

    public RetrySpec {
        Objects.requireNonNull(initialBackoff, "initialBackoff");
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalArgumentException("maxAttempts must be 1..5");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }
    }

    public static RetrySpec noRetry() {
        return new RetrySpec(1, Duration.ZERO, 1.0);
    }
}
