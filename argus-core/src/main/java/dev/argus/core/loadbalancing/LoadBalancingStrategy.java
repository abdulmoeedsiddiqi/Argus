package dev.argus.core.loadbalancing;

import dev.argus.core.pipeline.RequestContext;
import dev.argus.core.routing.UpstreamInstance;
import java.util.List;
import java.util.Optional;

/**
 * Strategy pattern: chooses one healthy instance. Implementations must be thread-safe,
 * because one strategy instance is shared by all request threads.
 * Planned implementations: RoundRobinStrategy, WeightedStrategy, LeastConnectionsStrategy.
 */
public interface LoadBalancingStrategy {

    Optional<UpstreamInstance> select(List<UpstreamInstance> healthyInstances, RequestContext context);
}
