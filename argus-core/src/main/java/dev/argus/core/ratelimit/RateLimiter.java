package dev.argus.core.ratelimit;

import dev.argus.core.exception.RateLimiterUnavailableException;
import dev.argus.core.model.RateLimitPolicy;

/**
 * Contract shared by every limiter (LocalTokenBucketLimiter, RedisRateLimiter,
 * FallbackRateLimiter). Substitutability rule (LSP): an implementation must never allow
 * more than {@code policy.capacity()} requests per refill period for one key, and must
 * signal an unreachable backing store only by throwing RateLimiterUnavailableException.
 */
public interface RateLimiter {

    RateLimitDecision tryAcquire(RateLimitKey key, RateLimitPolicy policy)
            throws RateLimiterUnavailableException;
}
