package dev.argus.core.loadbalancing;

import dev.argus.core.model.UpstreamInstance;
import dev.argus.core.pipeline.RequestContext;
import java.util.List;
import java.util.Optional;

/**
 * Strategy pattern: chooses one healthy instance. Implementations must be thread-safe,
 * because one strategy instance is shared by all request threads of a route.
 *
 * <p>Contract (Liskov): {@code select} returns only an instance from the given list, or empty
 * when the list is empty.
 */
public interface LoadBalancingStrategy {

    Optional<UpstreamInstance> select(List<UpstreamInstance> healthyInstances, RequestContext context);

    /**
     * Called just before a request is sent to {@code instance}. Strategies that do not track
     * requests in flight ignore it.
     */
    default void started(UpstreamInstance instance) {
        // no bookkeeping by default
    }

    /**
     * Called when that request is over, whether it succeeded, failed or timed out. Callers
     * invoke it in a {@code finally} block, so a count can never leak.
     */
    default void finished(UpstreamInstance instance) {
        // no bookkeeping by default
    }
}
