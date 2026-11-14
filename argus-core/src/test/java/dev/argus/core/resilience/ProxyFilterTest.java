package dev.argus.core.resilience;

import static dev.argus.core.pipeline.PipelineFixtures.getOrder42;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.Route;
import dev.argus.core.model.UpstreamInstance;
import dev.argus.core.model.UpstreamResponse;
import dev.argus.core.pipeline.FilterChain;
import dev.argus.core.pipeline.FilterResult;
import dev.argus.core.pipeline.RequestContext;
import dev.argus.core.routing.UpstreamClient;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ProxyFilter is the last element of the chain: it sends the request to the instance chosen by
 * the routing filter, through the UpstreamClient port, with the timeout of the matched route.
 * No circuit breaker or retry yet (those are FR-10 and FR-11).
 */
class ProxyFilterTest {

    private static final UpstreamInstance ORDERS_B =
            new UpstreamInstance("orders-b", "orders", URI.create("http://orders-b:8081"), 1, "v1");
    private static final Route ORDERS_ROUTE = Route.builder()
            .pathPrefix("/orders").service("orders").timeout(Duration.ofSeconds(2)).build();
    private static final FilterChain UNUSED_CHAIN = context -> {
        throw new AssertionError("the proxy filter is terminal and must not call the chain");
    };

    @Test
    @DisplayName("TC-01.1: sends the request to the instance chosen by routing, with the route timeout")
    void forwardsToTheChosenInstance() {
        AtomicReference<IncomingRequest> sentRequest = new AtomicReference<>();
        AtomicReference<UpstreamInstance> sentTo = new AtomicReference<>();
        AtomicReference<Duration> sentTimeout = new AtomicReference<>();
        UpstreamResponse upstreamAnswer = new UpstreamResponse(
                200, Map.of(), "{\"id\":42}".getBytes(StandardCharsets.UTF_8), "orders-b",
                Duration.ofMillis(5));
        UpstreamClient client = (request, target, timeout) -> {
            sentRequest.set(request);
            sentTo.set(target);
            sentTimeout.set(timeout);
            return upstreamAnswer;
        };
        RequestContext context = getOrder42().withRoute(ORDERS_ROUTE).withUpstream(ORDERS_B);

        FilterResult result = new ProxyFilter(client).filter(context, UNUSED_CHAIN);

        assertThat(sentRequest.get()).isSameAs(context.request());
        assertThat(sentTo.get()).isEqualTo(ORDERS_B);
        assertThat(sentTimeout.get()).isEqualTo(Duration.ofSeconds(2));
        assertThat(result).isEqualTo(new FilterResult.Forwarded(upstreamAnswer));
    }

    @Test
    @DisplayName("a request that reaches the proxy without a chosen instance is a wiring error")
    void failsWithoutAChosenInstance() {
        UpstreamClient unused = (request, target, timeout) -> null;
        RequestContext unrouted = getOrder42();

        assertThatThrownBy(() -> new ProxyFilter(unused).filter(unrouted, UNUSED_CHAIN))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("runs last in the chain: order 500, after routing (400)")
    void runsLast() {
        UpstreamClient unused = (request, target, timeout) -> null;

        assertThat(new ProxyFilter(unused).order()).isEqualTo(500);
    }

    @Test
    @DisplayName("refuses to be built without a client")
    void rejectsMissingClient() {
        assertThatThrownBy(() -> new ProxyFilter(null)).isInstanceOf(NullPointerException.class);
    }
}
