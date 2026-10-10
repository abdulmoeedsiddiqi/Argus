package dev.argus.core.resilience;

import dev.argus.core.model.IncomingRequest;
import java.time.Duration;
import java.util.Optional;

/**
 * Decides whether and when to retry. Implementations must return empty for
 * non-idempotent requests, so a retry can never duplicate a side effect.
 */
public interface RetryPolicy {

    Optional<Duration> nextDelay(IncomingRequest request, int attemptsSoFar, Throwable lastFailure);
}
