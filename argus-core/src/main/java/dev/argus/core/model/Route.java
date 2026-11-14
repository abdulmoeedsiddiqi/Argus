package dev.argus.core.model;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable route definition. Instances are created through {@link Builder}, because a route
 * has many optional parts. Immutable routes can be published to all request threads without
 * locking. An empty {@code methods} set means the route accepts every HTTP method.
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

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder pattern: collects the parts of a route step by step, fills in defaults, and
     * checks the route as a whole in {@link #build()} before the immutable record is created.
     * A builder is a short-lived, single-threaded helper; only the built Route is shared.
     */
    public static final class Builder {

        static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
        static final Duration MIN_TIMEOUT = Duration.ofMillis(100);
        static final Duration MAX_TIMEOUT = Duration.ofSeconds(60);

        private String id;
        private String pathPrefix;
        private final Set<HttpMethod> methods = EnumSet.noneOf(HttpMethod.class);
        private String serviceId;
        private LoadBalancingPolicy loadBalancing = LoadBalancingPolicy.ROUND_ROBIN;
        private Duration timeout = DEFAULT_TIMEOUT;
        private RetrySpec retry = RetrySpec.noRetry();
        private RateLimitPolicy rateLimit;
        private String canaryVersion;
        private int canaryPercent;
        private long version;

        private Builder() { }

        /** Optional. Defaults to the service id. */
        public Builder id(String newId) {
            this.id = newId;
            return this;
        }

        public Builder pathPrefix(String prefix) {
            this.pathPrefix = prefix;
            return this;
        }

        /** Optional. No methods means every method is accepted. */
        public Builder methods(HttpMethod... accepted) {
            methods.addAll(Set.of(accepted));
            return this;
        }

        public Builder service(String newServiceId) {
            this.serviceId = newServiceId;
            return this;
        }

        public Builder loadBalancing(LoadBalancingPolicy policy) {
            this.loadBalancing = policy;
            return this;
        }

        public Builder timeout(Duration newTimeout) {
            this.timeout = newTimeout;
            return this;
        }

        public Builder retry(RetrySpec spec) {
            this.retry = spec;
            return this;
        }

        public Builder rateLimit(RateLimitPolicy policy) {
            this.rateLimit = policy;
            return this;
        }

        /** Sends {@code percent}% of the route's traffic to instances of {@code newVersion}. */
        public Builder canary(String newVersion, int percent) {
            this.canaryVersion = newVersion;
            this.canaryPercent = percent;
            return this;
        }

        public Builder version(long newVersion) {
            this.version = newVersion;
            return this;
        }

        /** Validates all parts together and creates the immutable route. */
        public Route build() {
            require(pathPrefix != null && !pathPrefix.isBlank(), "pathPrefix is required");
            require(pathPrefix.startsWith("/"), "pathPrefix must start with '/': " + pathPrefix);
            require(serviceId != null && !serviceId.isBlank(), "service is required");
            require(loadBalancing != null, "loadBalancing is required");
            require(timeout != null
                            && timeout.compareTo(MIN_TIMEOUT) >= 0
                            && timeout.compareTo(MAX_TIMEOUT) <= 0,
                    "timeout must be between 100 ms and 60 s: " + timeout);
            require(retry != null, "retry is required");
            return new Route(
                    id == null ? serviceId : id, pathPrefix, methods, serviceId, loadBalancing,
                    timeout, retry, rateLimit, buildCanary(), version);
        }

        private CanarySplit buildCanary() {
            if (canaryVersion == null) {
                return null;
            }
            require(canaryPercent >= 0 && canaryPercent <= 100,
                    "canary percent must be 0..100: " + canaryPercent);
            return new CanarySplit(canaryVersion, canaryPercent);
        }

        private static void require(boolean condition, String message) {
            if (!condition) {
                throw new IllegalArgumentException("invalid route: " + message);
            }
        }
    }
}
