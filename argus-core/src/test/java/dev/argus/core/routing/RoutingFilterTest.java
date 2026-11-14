package dev.argus.core.routing;

import static dev.argus.core.pipeline.PipelineFixtures.forwarded;
import static dev.argus.core.routing.RoutingFixtures.instance;
import static dev.argus.core.routing.RoutingFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.argus.core.loadbalancing.DefaultLoadBalancingStrategyFactory;
import dev.argus.core.loadbalancing.LoadBalancingStrategy;
import dev.argus.core.loadbalancing.LoadBalancingStrategyFactory;
import dev.argus.core.model.HealthStatus;
import dev.argus.core.model.HttpMethod;
import dev.argus.core.model.LoadBalancingPolicy;
import dev.argus.core.model.Route;
import dev.argus.core.model.UpstreamInstance;
import dev.argus.core.pipeline.FilterChain;
import dev.argus.core.pipeline.FilterResult;
import dev.argus.core.pipeline.RejectionReason;
import dev.argus.core.pipeline.RequestContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RoutingFilter joins the parts of module M1: it matches the route (FR-01), keeps only healthy
 * instances (FR-03, QR-01), applies the canary split (FR-04) and lets the route's strategy
 * pick one instance (FR-02).
 */
class RoutingFilterTest {

    private static final UpstreamInstance ORDERS_A = instance("orders-a", "orders");
    private static final UpstreamInstance ORDERS_B = instance("orders-b", "orders");
    private static final UpstreamInstance PRODUCTS_V1 = instance("products-v1", "products", 1, "v1");
    private static final UpstreamInstance PRODUCTS_V2 = instance("products-v2", "products", 1, "v2");
    private static final Route ORDERS_ROUTE = Route.builder()
            .pathPrefix("/orders").service("orders").methods(HttpMethod.GET).build();

    private final SnapshotRouteRegistry routes = new SnapshotRouteRegistry();
    private final InMemoryUpstreamRegistry upstreams = new InMemoryUpstreamRegistry();
    private final RecordingChain chain = new RecordingChain();
    private RoutingFilter filter;

    @BeforeEach
    void setUp() {
        routes.replaceAll(List.of(ORDERS_ROUTE));
        upstreams.replaceAll(List.of(ORDERS_A, ORDERS_B, PRODUCTS_V1, PRODUCTS_V2));
        filter = new RoutingFilter(
                routes, upstreams, new DefaultLoadBalancingStrategyFactory(), new CanarySelector());
    }

    @Test
    @DisplayName("TC-01.1: a matched request continues with its route and a chosen instance")
    void matchedRequestContinuesWithRouteAndInstance() {
        FilterResult result = filter.filter(context(HttpMethod.GET, "/orders/42"), chain);

        assertThat(result).isSameAs(chain.answer);
        assertThat(chain.seen.matchedRoute()).contains(ORDERS_ROUTE);
        assertThat(chain.seen.selectedUpstream()).contains(ORDERS_A);
    }

    @Test
    @DisplayName("TC-01.2: an unknown path is rejected with 404 ROUTE_NOT_FOUND and goes no further")
    void unknownPathIsRejected() {
        FilterResult result = filter.filter(context(HttpMethod.GET, "/payments/1"), chain);

        assertThat(result).isInstanceOfSatisfying(FilterResult.Rejected.class, rejected -> {
            assertThat(rejected.httpStatus()).isEqualTo(404);
            assertThat(rejected.reason()).isEqualTo(RejectionReason.ROUTE_NOT_FOUND);
        });
        assertThat(chain.calls).isZero();
    }

    @Test
    @DisplayName("TC-01.3: a method the route does not accept is rejected with 404 ROUTE_NOT_FOUND")
    void wrongMethodIsRejected() {
        FilterResult result = filter.filter(context(HttpMethod.POST, "/orders"), chain);

        assertThat(result).isInstanceOfSatisfying(FilterResult.Rejected.class, rejected -> {
            assertThat(rejected.httpStatus()).isEqualTo(404);
            assertThat(rejected.reason()).isEqualTo(RejectionReason.ROUTE_NOT_FOUND);
        });
        assertThat(chain.calls).isZero();
    }

    @Test
    @DisplayName("FR-02: the route keeps its strategy between requests (round robin alternates)")
    void strategyStateIsKeptBetweenRequests() {
        List<String> chosen = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            filter.filter(context(HttpMethod.GET, "/orders/42"), chain);
            chosen.add(chain.seen.selectedUpstream().orElseThrow().id());
        }

        assertThat(chosen).containsExactly("orders-a", "orders-b", "orders-a", "orders-b");
    }

    @Test
    @DisplayName("TC-Q01.1: an UNHEALTHY instance never receives a request")
    void unhealthyInstanceIsNeverChosen() {
        upstreams.updateHealth("orders-a", HealthStatus.UNHEALTHY);

        for (int i = 0; i < 100; i++) {
            filter.filter(context(HttpMethod.GET, "/orders/42"), chain);
            assertThat(chain.seen.selectedUpstream()).contains(ORDERS_B);
        }
    }

    @Test
    @DisplayName("QR-01: with no healthy instance the request is rejected with 503 NO_HEALTHY_UPSTREAM")
    void noHealthyInstanceIsRejected() {
        upstreams.updateHealth("orders-a", HealthStatus.UNHEALTHY);
        upstreams.updateHealth("orders-b", HealthStatus.UNHEALTHY);

        FilterResult result = filter.filter(context(HttpMethod.GET, "/orders/42"), chain);

        assertThat(result).isInstanceOfSatisfying(FilterResult.Rejected.class, rejected -> {
            assertThat(rejected.httpStatus()).isEqualTo(503);
            assertThat(rejected.reason()).isEqualTo(RejectionReason.NO_HEALTHY_UPSTREAM);
        });
        assertThat(chain.calls).isZero();
    }

    @Test
    @DisplayName("FR-04: a 100% canary route sends the request to the canary version")
    void canaryRouteUsesCanaryInstances() {
        routes.replaceAll(List.of(Route.builder()
                .pathPrefix("/products").service("products").canary("v2", 100).build()));

        filter.filter(context(HttpMethod.GET, "/products/3"), chain);

        assertThat(chain.seen.selectedUpstream()).contains(PRODUCTS_V2);
    }

    @Test
    @DisplayName("FR-02: the factory is asked once per route, with the policy of that route")
    void createsOneStrategyPerRoute() {
        RecordingFactory factory = new RecordingFactory();
        routes.replaceAll(List.of(Route.builder().pathPrefix("/orders").service("orders")
                .loadBalancing(LoadBalancingPolicy.WEIGHTED).build()));
        RoutingFilter recording = new RoutingFilter(routes, upstreams, factory, new CanarySelector());

        for (int i = 0; i < 5; i++) {
            recording.filter(context(HttpMethod.GET, "/orders/42"), chain);
        }

        assertThat(factory.requested).containsExactly(LoadBalancingPolicy.WEIGHTED);
    }

    @Test
    @DisplayName("FR-02: when a route changes its policy, a new strategy is created for it")
    void policyChangeCreatesANewStrategy() {
        RecordingFactory factory = new RecordingFactory();
        RoutingFilter recording = new RoutingFilter(routes, upstreams, factory, new CanarySelector());
        recording.filter(context(HttpMethod.GET, "/orders/42"), chain);

        routes.replaceAll(List.of(Route.builder().pathPrefix("/orders").service("orders")
                .loadBalancing(LoadBalancingPolicy.LEAST_CONNECTIONS).build()));
        recording.filter(context(HttpMethod.GET, "/orders/42"), chain);

        assertThat(factory.requested).containsExactly(
                LoadBalancingPolicy.ROUND_ROBIN, LoadBalancingPolicy.LEAST_CONNECTIONS);
    }

    @Test
    @DisplayName("TC-02.3: the strategy is told when the request starts and when it ends")
    void reportsStartAndFinishToTheStrategy() {
        RecordingFactory factory = new RecordingFactory();
        RoutingFilter recording = new RoutingFilter(routes, upstreams, factory, new CanarySelector());

        recording.filter(context(HttpMethod.GET, "/orders/42"), chain);

        assertThat(factory.events).containsExactly("started orders-a", "finished orders-a");
    }

    @Test
    @DisplayName("TC-02.3: the end is reported even when the rest of the chain throws")
    void reportsFinishWhenTheChainFails() {
        RecordingFactory factory = new RecordingFactory();
        RoutingFilter recording = new RoutingFilter(routes, upstreams, factory, new CanarySelector());
        FilterChain failing = context -> {
            throw new IllegalStateException("upstream call failed");
        };

        assertThatThrownBy(() -> recording.filter(context(HttpMethod.GET, "/orders/42"), failing))
                .isInstanceOf(IllegalStateException.class);
        assertThat(factory.events).containsExactly("started orders-a", "finished orders-a");
    }

    @Test
    @DisplayName("runs after rate limiting (300) and before proxying (500): order 400")
    void hasOrder400() {
        assertThat(filter.order()).isEqualTo(400);
    }

    private static RequestContext context(HttpMethod method, String path) {
        return RequestContext.of(request(method, path));
    }

    /** Terminal stand-in for the rest of the chain: remembers the context it was given. */
    private static final class RecordingChain implements FilterChain {
        private final FilterResult answer = forwarded("ok");
        private RequestContext seen;
        private int calls;

        @Override
        public FilterResult proceed(RequestContext context) {
            seen = context;
            calls++;
            return answer;
        }
    }

    /** Factory whose strategies always pick the first instance and log their hook calls. */
    private static final class RecordingFactory implements LoadBalancingStrategyFactory {
        private final List<LoadBalancingPolicy> requested = new ArrayList<>();
        private final List<String> events = new ArrayList<>();

        @Override
        public LoadBalancingStrategy create(LoadBalancingPolicy policy) {
            requested.add(policy);
            return new LoadBalancingStrategy() {
                @Override
                public Optional<UpstreamInstance> select(
                        List<UpstreamInstance> healthyInstances, RequestContext context) {
                    return healthyInstances.stream().findFirst();
                }

                @Override
                public void started(UpstreamInstance instance) {
                    events.add("started " + instance.id());
                }

                @Override
                public void finished(UpstreamInstance instance) {
                    events.add("finished " + instance.id());
                }
            };
        }
    }
}
