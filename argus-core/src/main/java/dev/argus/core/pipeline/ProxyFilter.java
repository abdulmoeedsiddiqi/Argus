package dev.argus.core.pipeline;

import dev.argus.core.model.UpstreamInstance;
import dev.argus.core.model.UpstreamResponse;
import dev.argus.core.routing.UpstreamClient;
import java.time.Duration;
import java.util.Objects;

/**
 * Last filter of the chain: forwards the request to an upstream instance through the
 * {@link UpstreamClient} port and returns the upstream's response.
 *
 * <p>Walking skeleton: the target instance and timeout are fixed at construction. The circuit
 * breaker, the retry policy and a target chosen per request by the routing filter are added
 * with modules M1 and M4.
 */
public final class ProxyFilter implements GatewayFilter {

    /** Runs after authentication (100), enforcement (200), rate limit (300) and routing (400). */
    public static final int ORDER = 500;

    private final UpstreamClient client;
    private final UpstreamInstance target;
    private final Duration timeout;

    public ProxyFilter(UpstreamClient client, UpstreamInstance target, Duration timeout) {
        this.client = Objects.requireNonNull(client, "client");
        this.target = Objects.requireNonNull(target, "target");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public FilterResult filter(RequestContext context, FilterChain chain) {
        UpstreamResponse response = client.send(context.request(), target, timeout);
        return new FilterResult.Forwarded(response);
    }

    @Override
    public int order() {
        return ORDER;
    }
}
