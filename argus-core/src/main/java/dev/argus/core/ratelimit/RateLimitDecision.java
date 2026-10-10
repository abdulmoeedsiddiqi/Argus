package dev.argus.core.ratelimit;

import java.time.Duration;

/** Result of a rate-limit check, including headers the gateway returns to the client. */
public record RateLimitDecision(boolean allowed, long remaining, Duration retryAfter) {
}
