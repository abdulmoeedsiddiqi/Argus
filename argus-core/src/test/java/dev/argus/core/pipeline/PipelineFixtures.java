package dev.argus.core.pipeline;

import dev.argus.core.model.HttpMethod;
import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.UpstreamResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Small builders shared by the pipeline tests. */
public final class PipelineFixtures {

    private PipelineFixtures() { }

    public static RequestContext getOrder42() {
        return RequestContext.of(new IncomingRequest(
                "req-1", HttpMethod.GET, "/orders/42", Map.of(), null, "127.0.0.1",
                Instant.parse("2026-11-14T00:00:00Z")));
    }

    public static FilterResult.Forwarded forwarded(String body) {
        return new FilterResult.Forwarded(new UpstreamResponse(
                200, Map.of(), body.getBytes(StandardCharsets.UTF_8), "orders-a", Duration.ZERO));
    }

    /** A filter that writes its name to the log, then lets the request continue. */
    public static GatewayFilter passing(String name, int order, List<String> log) {
        return new GatewayFilter() {
            @Override
            public FilterResult filter(RequestContext context, FilterChain chain) {
                log.add(name);
                return chain.proceed(context);
            }

            @Override
            public int order() {
                return order;
            }
        };
    }

    /** A filter that writes its name to the log, then answers without calling the chain. */
    public static GatewayFilter answering(String name, int order, List<String> log, FilterResult result) {
        return new GatewayFilter() {
            @Override
            public FilterResult filter(RequestContext context, FilterChain chain) {
                log.add(name);
                return result;
            }

            @Override
            public int order() {
                return order;
            }
        };
    }
}
