package dev.argus.core.loadbalancing;

import dev.argus.core.model.LoadBalancingPolicy;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Factory Method: turns a configured policy into a new strategy. Callers see only the
 * {@link LoadBalancingStrategy} interface. A new algorithm is one new class plus one line in
 * the table below; no caller and no existing strategy changes (Open/Closed).
 */
public final class DefaultLoadBalancingStrategyFactory implements LoadBalancingStrategyFactory {

    private final Map<LoadBalancingPolicy, Supplier<LoadBalancingStrategy>> creators =
            new EnumMap<>(LoadBalancingPolicy.class);

    public DefaultLoadBalancingStrategyFactory() {
        creators.put(LoadBalancingPolicy.ROUND_ROBIN, RoundRobinStrategy::new);
        creators.put(LoadBalancingPolicy.WEIGHTED, WeightedStrategy::new);
        creators.put(LoadBalancingPolicy.LEAST_CONNECTIONS, LeastConnectionsStrategy::new);
    }

    /** Returns a new strategy each time: strategies hold per-route state (position, counts). */
    @Override
    public LoadBalancingStrategy create(LoadBalancingPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        Supplier<LoadBalancingStrategy> creator = creators.get(policy);
        if (creator == null) {
            throw new IllegalArgumentException("no strategy registered for policy " + policy);
        }
        return creator.get();
    }
}
