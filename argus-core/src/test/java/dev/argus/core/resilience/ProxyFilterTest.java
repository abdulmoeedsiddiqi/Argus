package dev.argus.core.resilience;

import static dev.argus.core.pipeline.PipelineFixtures.getOrder42;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.argus.core.model.IncomingRequest;
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
 * ProxyFilter is the last element of the chain: it forwards the request to an upstream
 * instance through the UpstreamClient port. Walking skeleton (UC-01): one fixed instance,
 * no circuit breaker or retry yet (those are FR-10 and FR-11).
 */
class ProxyFilterTest {

    private static final UpstreamInstance ORDERS =
            new UpstreamInstance("orders-a", "orders", URI.create("http://orders-a:8081"), 1, "v1");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final FilterChain UNUSED_CHAIN = context -> {
        throw new AssertionError("the proxy filter is terminal and must not call the chain");
    };

    @Test
    @DisplayName("forwards the request to the configured instance and returns its response")
    void forwardsRequestToConfiguredInstance() {
        AtomicReference<IncomingRequest> sentRequest = new AtomicReference<>();
        AtomicReference<UpstreamInstance> sentTo = new AtomicReference<>();
        AtomicReference<Duration> sentTimeout = new AtomicReference<>();
        UpstreamResponse upstreamAnswer = new UpstreamResponse(
                200, Map.of(), "{\"id\":42}".getBytes(StandardCharsets.UTF_8), "orders-a",
                Duration.ofMillis(5));
        UpstreamClient client = (request, target, timeout) -> {
            sentRequest.set(request);
            sentTo.set(target);
            sentTimeout.set(timeout);
            return upstreamAnswer;
        };
        RequestContext context = getOrder42();

        FilterResult result = new ProxyFilter(client, ORDERS, TIMEOUT).filter(context, UNUSED_CHAIN);

        assertThat(sentRequest.get()).isSameAs(context.request());
        assertThat(sentTo.get()).isEqualTo(ORDERS);
        assertThat(sentTimeout.get()).isEqualTo(TIMEOUT);
        assertThat(result).isEqualTo(new FilterResult.Forwarded(upstreamAnswer));
    }

    @Test
    @DisplayName("runs last in the chain: order 500, after routing (400)")
    void runsLast() {
        UpstreamClient unused = (request, target, timeout) -> null;

        assertThat(new ProxyFilter(unused, ORDERS, TIMEOUT).order()).isEqualTo(500);
    }

    @Test
    @DisplayName("refuses to be built without a client, a target or a timeout")
    void rejectsMissingCollaborators() {
        UpstreamClient client = (request, target, timeout) -> null;

        assertThatThrownBy(() -> new ProxyFilter(null, ORDERS, TIMEOUT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProxyFilter(client, null, TIMEOUT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProxyFilter(client, ORDERS, null))
                .isInstanceOf(NullPointerException.class);
    }
}
