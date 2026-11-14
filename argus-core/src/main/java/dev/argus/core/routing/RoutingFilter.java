package dev.argus.core.routing;

import dev.argus.core.loadbalancing.LoadBalancingStrategy;
import dev.argus.core.loadbalancing.LoadBalancingStrategyFactory;
import dev.argus.core.model.LoadBalancingPolicy;
import dev.argus.core.model.Route;
import dev.argus.core.model.UpstreamInstance;
import dev.argus.core.pipeline.FilterChain;
import dev.argus.core.pipeline.FilterResult;
import dev.argus.core.pipeline.GatewayFilter;
import dev.argus.core.pipeline.RejectionReason;
import dev.argus.core.pipeline.RequestContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routing step of the chain (module M1). For each request it:
 * <ol>
 *   <li>matches the route by path and method (FR-01), or rejects with 404;</li>
 *   <li>takes the healthy instances of the route's service (FR-03, QR-01);</li>
 *   <li>applies the canary split, if the route has one (FR-04);</li>
 *   <li>lets the route's load-balancing strategy pick one instance (FR-02), or rejects with
 *       503 when none is available;</li>
 *   <li>passes the route and the chosen instance on in the request context.</li>
 * </ol>
 * It knows only interfaces: which registry, which strategy and how instances are checked can
 * all change without touching this class.
 */
public final class RoutingFilter implements GatewayFilter {

    /** Runs after rate limiting (300) and before proxying (500). */
    public static final int ORDER = 400;

    private final RouteRegistry routes;
    private final UpstreamRegistry upstreams;
    private final LoadBalancingStrategyFactory strategyFactory;
    private final CanarySelector canarySelector;

    /**
     * One strategy per route id. A strategy has state (round-robin position, requests in
     * flight) that must survive from one request to the next, so it is created once and reused.
     */
    private final ConcurrentHashMap<String, RouteStrategy> strategies = new ConcurrentHashMap<>();

    public RoutingFilter(
            RouteRegistry routes,
            UpstreamRegistry upstreams,
            LoadBalancingStrategyFactory strategyFactory,
            CanarySelector canarySelector) {
        this.routes = Objects.requireNonNull(routes, "routes");
        this.upstreams = Objects.requireNonNull(upstreams, "upstreams");
        this.strategyFactory = Objects.requireNonNull(strategyFactory, "strategyFactory");
        this.canarySelector = Objects.requireNonNull(canarySelector, "canarySelector");
    }

    @Override
    public FilterResult filter(RequestContext context, FilterChain chain) {
        Optional<Route> matched = routes.match(context.request());
        if (matched.isEmpty()) {
            return new FilterResult.Rejected(
                    404, RejectionReason.ROUTE_NOT_FOUND, "no route matches this path and method");
        }
        Route route = matched.get();

        List<UpstreamInstance> healthy = upstreams.healthyInstances(route.serviceId());
        List<UpstreamInstance> candidates = canarySelector.filterByVersion(healthy, route.canary());
        LoadBalancingStrategy strategy = strategyFor(route);
        Optional<UpstreamInstance> chosen = strategy.select(candidates, context);
        if (chosen.isEmpty()) {
            return new FilterResult.Rejected(
                    503, RejectionReason.NO_HEALTHY_UPSTREAM,
                    "no healthy instance of service " + route.serviceId());
        }
        UpstreamInstance instance = chosen.get();

        strategy.started(instance);
        try {
            return chain.proceed(context.withRoute(route).withUpstream(instance));
        } finally {
            // Runs on success, error and timeout alike, so an in-flight count can never leak.
            strategy.finished(instance);
        }
    }

    @Override
    public int order() {
        return ORDER;
    }

    /** Returns the route's strategy, creating a new one if the route is new or changed policy. */
    private LoadBalancingStrategy strategyFor(Route route) {
        LoadBalancingPolicy policy = route.loadBalancing();
        return strategies.compute(route.id(), (id, current) ->
                current != null && current.policy() == policy
                        ? current
                        : new RouteStrategy(policy, strategyFactory.create(policy)))
                .strategy();
    }

    /** A strategy together with the policy it was created for. */
    private record RouteStrategy(LoadBalancingPolicy policy, LoadBalancingStrategy strategy) { }
}
