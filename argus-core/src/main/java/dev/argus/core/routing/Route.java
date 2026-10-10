package dev.argus.core.routing;

import dev.argus.core.loadbalancing.LoadBalancingPolicy;
import dev.argus.core.pipeline.HttpMethod;
import dev.argus.core.ratelimit.RateLimitPolicy;
import dev.argus.core.resilience.RetrySpec;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable route definition. Instances are created through a Builder
 * (added in Milestone 2) because a route has many optional parts.
 * Immutable routes can be published to all request threads without locking.
 */
public record Route(
        String id,
        String pathPrefix,
        Set<HttpMethod> methods,
        String serviceId,
        LoadBalancingPolicy loadBalancing,
        Duration timeout,
        RetrySpec retry,
        RateLimitPolicy rateLimit,
        CanarySplit canary,
        long version) {

    public Route {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(pathPrefix, "pathPrefix");
        Objects.requireNonNull(serviceId, "serviceId");
        Objects.requireNonNull(loadBalancing, "loadBalancing");
        Objects.requireNonNull(timeout, "timeout");
        methods = methods == null ? Set.of() : Set.copyOf(methods);
    }
}
