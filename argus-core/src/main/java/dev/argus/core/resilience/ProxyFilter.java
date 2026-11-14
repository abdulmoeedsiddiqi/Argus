package dev.argus.core.resilience;

import dev.argus.core.model.Route;
import dev.argus.core.model.UpstreamInstance;
import dev.argus.core.model.UpstreamResponse;
import dev.argus.core.pipeline.FilterChain;
import dev.argus.core.pipeline.FilterResult;
import dev.argus.core.pipeline.GatewayFilter;
import dev.argus.core.pipeline.RequestContext;
import dev.argus.core.routing.UpstreamClient;
import java.util.Objects;

/**
 * Last filter of the chain: sends the request to the instance chosen by the routing filter,
 * through the {@link UpstreamClient} port, with the timeout of the matched route, and returns
 * the upstream's response.
 *
 * <p>The circuit breaker and the retry policy are added here with module M4.
 */
public final class ProxyFilter implements GatewayFilter {

    /** Runs after authentication (100), enforcement (200), rate limit (300) and routing (400). */
    public static final int ORDER = 500;

    private final UpstreamClient client;

    public ProxyFilter(UpstreamClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public FilterResult filter(RequestContext context, FilterChain chain) {
        UpstreamInstance target = context.selectedUpstream().orElseThrow(() ->
                new IllegalStateException("no upstream chosen: the routing filter must run first"));
        Route route = context.matchedRoute().orElseThrow(() ->
                new IllegalStateException("no route matched: the routing filter must run first"));
        UpstreamResponse response = client.send(context.request(), target, route.timeout());
        return new FilterResult.Forwarded(response);
    }

    @Override
    public int order() {
        return ORDER;
    }
}
