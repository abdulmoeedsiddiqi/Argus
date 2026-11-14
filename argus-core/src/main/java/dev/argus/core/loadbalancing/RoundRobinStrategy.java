package dev.argus.core.loadbalancing;

import dev.argus.core.model.UpstreamInstance;
import dev.argus.core.pipeline.RequestContext;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Picks the instances in turn. The position is an {@link AtomicInteger}: each request takes the
 * next number with one atomic step, so no lock is needed and no two requests share a number.
 */
public final class RoundRobinStrategy implements LoadBalancingStrategy {

    private final AtomicInteger position;

    public RoundRobinStrategy() {
        this(0);
    }

    /** For tests: start at a chosen position, for example just before the int overflows. */
    RoundRobinStrategy(int startPosition) {
        this.position = new AtomicInteger(startPosition);
    }

    @Override
    public Optional<UpstreamInstance> select(
            List<UpstreamInstance> healthyInstances, RequestContext context) {
        if (healthyInstances.isEmpty()) {
            return Optional.empty();
        }
        // floorMod keeps the index valid after the counter overflows to a negative number.
        int index = Math.floorMod(position.getAndIncrement(), healthyInstances.size());
        return Optional.of(healthyInstances.get(index));
    }
}
