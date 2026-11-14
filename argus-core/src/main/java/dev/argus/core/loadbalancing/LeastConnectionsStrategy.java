package dev.argus.core.loadbalancing;

import dev.argus.core.model.UpstreamInstance;
import dev.argus.core.pipeline.RequestContext;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Picks the instance with the fewest requests in flight.
 *
 * <p>Thread-safety: one {@link AtomicInteger} per instance in a {@link ConcurrentHashMap}, so
 * counting needs no global lock. The caller reports {@link #started} before sending and
 * {@link #finished} in a {@code finally} block, so a failed or timed-out request cannot leave
 * a count behind and starve the instance.
 */
public final class LeastConnectionsStrategy implements LoadBalancingStrategy {

    private final ConcurrentHashMap<String, AtomicInteger> active = new ConcurrentHashMap<>();

    @Override
    public Optional<UpstreamInstance> select(
            List<UpstreamInstance> healthyInstances, RequestContext context) {
        UpstreamInstance leastBusy = null;
        int fewest = Integer.MAX_VALUE;
        for (UpstreamInstance instance : healthyInstances) {
            int inFlight = activeRequests(instance);
            if (inFlight < fewest) {
                fewest = inFlight;
                leastBusy = instance;
            }
        }
        return Optional.ofNullable(leastBusy);
    }

    @Override
    public void started(UpstreamInstance instance) {
        active.computeIfAbsent(instance.id(), id -> new AtomicInteger()).incrementAndGet();
    }

    @Override
    public void finished(UpstreamInstance instance) {
        AtomicInteger count = active.get(instance.id());
        if (count != null) {
            count.decrementAndGet();
        }
    }

    /** Requests currently in flight to the instance; zero if it was never used. */
    public int activeRequests(UpstreamInstance instance) {
        AtomicInteger count = active.get(instance.id());
        return count == null ? 0 : count.get();
    }
}
