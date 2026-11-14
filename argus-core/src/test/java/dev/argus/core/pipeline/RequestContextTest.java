package dev.argus.core.pipeline;

import static dev.argus.core.pipeline.PipelineFixtures.getOrder42;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.argus.core.model.Principal;
import dev.argus.core.model.Route;
import dev.argus.core.model.UpstreamInstance;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The request context is immutable: each filter derives a new one for the next filter. */
class RequestContextTest {

    private static final UpstreamInstance ORDERS_A =
            new UpstreamInstance("orders-a", "orders", URI.create("http://orders-a:8081"), 1, "v1");
    private static final Route ORDERS_ROUTE =
            Route.builder().pathPrefix("/orders").service("orders").build();

    @Test
    @DisplayName("a new context has no principal, route or chosen instance yet")
    void startsWithOnlyTheRequest() {
        RequestContext context = getOrder42();

        assertThat(context.authenticatedPrincipal()).isEmpty();
        assertThat(context.matchedRoute()).isEmpty();
        assertThat(context.selectedUpstream()).isEmpty();
    }

    @Test
    @DisplayName("withUpstream returns a new context carrying the chosen instance")
    void withUpstreamDerivesANewContext() {
        RequestContext original = getOrder42();

        RequestContext routed = original.withUpstream(ORDERS_A);

        assertThat(routed.selectedUpstream()).contains(ORDERS_A);
        assertThat(original.selectedUpstream()).isEmpty();
        assertThat(routed.request()).isSameAs(original.request());
    }

    @Test
    @DisplayName("each with-method keeps what earlier filters added")
    void withMethodsKeepEarlierValues() {
        Principal shopApp = new Principal("shop-app", "key-1", "free");

        RequestContext context = getOrder42()
                .withPrincipal(shopApp)
                .withRoute(ORDERS_ROUTE)
                .withUpstream(ORDERS_A);

        assertThat(context.authenticatedPrincipal()).contains(shopApp);
        assertThat(context.matchedRoute()).contains(ORDERS_ROUTE);
        assertThat(context.selectedUpstream()).contains(ORDERS_A);
        assertThat(context.withPrincipal(shopApp).selectedUpstream()).contains(ORDERS_A);
        assertThat(context.withRoute(ORDERS_ROUTE).selectedUpstream()).contains(ORDERS_A);
    }

    @Test
    @DisplayName("a missing instance is rejected")
    void rejectsNullUpstream() {
        assertThatThrownBy(() -> getOrder42().withUpstream(null))
                .isInstanceOf(NullPointerException.class);
    }
}
