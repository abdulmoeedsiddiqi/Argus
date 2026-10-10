package dev.argus.core.resilience;

import java.time.Duration;
import java.util.Objects;

/** Breaker tuning: failures that open it, how long it stays open, trial calls when half-open. */
public record CircuitBreakerPolicy(int failureThreshold, Duration openDuration, int halfOpenTrialCalls) {

    public CircuitBreakerPolicy {
        Objects.requireNonNull(openDuration, "openDuration");
        if (failureThreshold < 1 || halfOpenTrialCalls < 1) {
            throw new IllegalArgumentException("thresholds must be >= 1");
        }
    }
}
