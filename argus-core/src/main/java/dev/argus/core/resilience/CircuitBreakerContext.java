package dev.argus.core.resilience;

import dev.argus.core.time.TimeSource;

/** What a state needs from the breaker: its policy, the clock, and a safe way to transition. */
public interface CircuitBreakerContext {

    CircuitBreakerPolicy policy();

    TimeSource timeSource();

    /** Atomically replaces {@code expected} with {@code next}; returns false if another thread won. */
    boolean transition(CircuitState expected, CircuitState next);
}
