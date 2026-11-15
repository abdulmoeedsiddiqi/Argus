package dev.argus.gateway.events;

import dev.argus.contracts.ConfigChange;
import dev.argus.contracts.RouteConfig;
import dev.argus.contracts.UpstreamConfig;
import dev.argus.core.events.ConfigurationListener;
import dev.argus.core.model.HttpMethod;
import dev.argus.core.model.LoadBalancingPolicy;
import dev.argus.core.model.RetrySpec;
import dev.argus.core.model.Route;
import dev.argus.core.model.UpstreamInstance;
import dev.argus.core.routing.InMemoryUpstreamRegistry;
import dev.argus.core.routing.RouteRegistry;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observer (FR-12): applies ROUTE and UPSTREAM changes from the configuration topic.
 *
 * <p>It keeps the current routes and backend copies by id. For each change it updates that
 * private map, then hands a complete new list to the registry, which builds an immutable
 * snapshot and swaps it in. Request threads therefore never see a half-applied change.
 */
public final class RouteConfigApplier implements ConfigurationListener {

    private static final Logger LOG = LoggerFactory.getLogger(RouteConfigApplier.class);
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(100);
    private static final double RETRY_MULTIPLIER = 2.0;

    private final RouteRegistry routeRegistry;
    private final InMemoryUpstreamRegistry upstreamRegistry;
    private final ConfigChangeReader reader;
    private final Map<String, Route> routes = new HashMap<>();
    private final Map<String, UpstreamInstance> upstreams = new HashMap<>();
    /** Guards the two maps. Changes arrive from one consumer thread, so it is never contended. */
    private final ReentrantLock lock = new ReentrantLock();

    public RouteConfigApplier(
            RouteRegistry routeRegistry,
            InMemoryUpstreamRegistry upstreamRegistry,
            ConfigChangeReader reader) {
        this.routeRegistry = routeRegistry;
        this.upstreamRegistry = upstreamRegistry;
        this.reader = reader;
    }

    @Override
    public void onChange(ConfigChange change) {
        lock.lock();
        try {
            switch (change.entityType()) {
                case ROUTE -> applyRoute(change);
                case UPSTREAM -> applyUpstream(change);
                default -> { }
            }
        } catch (RuntimeException invalid) {
            // A bad record must not stop the gateway: keep what is in use and say so.
            LOG.warn("ignored configuration change {}: {}", change.key(), invalid.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private void applyRoute(ConfigChange change) {
        Map<String, Route> next = new HashMap<>(routes);
        if (change.isDeletion()) {
            next.remove(change.entityId());
        } else {
            next.put(change.entityId(), toRoute(reader.payload(change, RouteConfig.class)));
        }
        // replaceAll validates the whole table; only if it succeeds do we remember the change.
        routeRegistry.replaceAll(next.values());
        routes.clear();
        routes.putAll(next);
    }

    private void applyUpstream(ConfigChange change) {
        Map<String, UpstreamInstance> next = new HashMap<>(upstreams);
        if (change.isDeletion()) {
            next.remove(change.entityId());
        } else {
            next.put(change.entityId(), toInstance(reader.payload(change, UpstreamConfig.class)));
        }
        upstreamRegistry.replaceAll(next.values());
        upstreams.clear();
        upstreams.putAll(next);
    }

    private static Route toRoute(RouteConfig config) {
        Route.Builder builder = Route.builder()
                .id(config.id())
                .pathPrefix(config.pathPrefix())
                .service(config.serviceId())
                .loadBalancing(LoadBalancingPolicy.valueOf(config.loadBalancing()))
                .timeout(Duration.ofMillis(config.timeoutMs()))
                .retry(toRetry(config.retryAttempts()))
                .version(config.version());
        builder.methods(config.methods().stream().map(HttpMethod::valueOf).toArray(HttpMethod[]::new));
        if (config.canaryVersion() != null && config.canaryPercent() != null) {
            builder.canary(config.canaryVersion(), config.canaryPercent());
        }
        return builder.build();
    }

    private static RetrySpec toRetry(int attempts) {
        return attempts <= 1
                ? RetrySpec.noRetry()
                : new RetrySpec(attempts, RETRY_BACKOFF, RETRY_MULTIPLIER);
    }

    private static UpstreamInstance toInstance(UpstreamConfig config) {
        return new UpstreamInstance(
                config.id(), config.serviceId(), URI.create(config.baseUri()),
                config.weight(), config.version());
    }
}
