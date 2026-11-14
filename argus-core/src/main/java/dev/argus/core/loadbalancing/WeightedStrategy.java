package dev.argus.core.loadbalancing;

import dev.argus.core.model.UpstreamInstance;
import dev.argus.core.pipeline.RequestContext;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

/**
 * Picks an instance with a probability proportional to its weight: with weights 3 and 1 the
 * first instance gets about 75% of the requests. The strategy keeps no state, so it is
 * thread-safe; the default random source is {@link ThreadLocalRandom}, which has no contention.
 */
public final class WeightedStrategy implements LoadBalancingStrategy {

    /** Returns a random int in [0, bound). */
    private final IntUnaryOperator randomBelow;

    public WeightedStrategy() {
        this(bound -> ThreadLocalRandom.current().nextInt(bound));
    }

    /** For tests: a seeded or fixed random source makes the choice repeatable. */
    WeightedStrategy(IntUnaryOperator randomBelow) {
        this.randomBelow = Objects.requireNonNull(randomBelow, "randomBelow");
    }

    @Override
    public Optional<UpstreamInstance> select(
            List<UpstreamInstance> healthyInstances, RequestContext context) {
        if (healthyInstances.isEmpty()) {
            return Optional.empty();
        }
        int totalWeight = 0;
        for (UpstreamInstance instance : healthyInstances) {
            totalWeight += instance.weight();
        }
        // Think of totalWeight slots in a row; each instance owns as many slots as its weight.
        int slot = randomBelow.applyAsInt(totalWeight);
        for (UpstreamInstance instance : healthyInstances) {
            slot -= instance.weight();
            if (slot < 0) {
                return Optional.of(instance);
            }
        }
        return Optional.of(healthyInstances.get(healthyInstances.size() - 1));
    }
}
