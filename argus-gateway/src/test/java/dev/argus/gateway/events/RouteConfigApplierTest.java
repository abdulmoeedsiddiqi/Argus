package dev.argus.gateway.events;

import static org.assertj.core.api.Assertions.assertThat;

import dev.argus.contracts.ConfigChange;
import dev.argus.contracts.ConfigChange.EntityType;
import dev.argus.contracts.RouteConfig;
import dev.argus.core.model.HttpMethod;
import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.LoadBalancingPolicy;
import dev.argus.core.model.Route;
import dev.argus.core.model.UpstreamInstance;
import dev.argus.core.routing.InMemoryUpstreamRegistry;
import dev.argus.core.routing.SnapshotRouteRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FR-12: configuration changes from the topic become the gateway's routes and backend copies. */
class RouteConfigApplierTest {

    private final SnapshotRouteRegistry routes = new SnapshotRouteRegistry();
    private final InMemoryUpstreamRegistry upstreams = new InMemoryUpstreamRegistry();
    private final RouteConfigApplier applier =
            new RouteConfigApplier(routes, upstreams, new ConfigChangeReader());

    @Test
    @DisplayName("FR-12: a ROUTE change adds the route to the route table")
    void routeChangeAddsTheRoute() {
        applier.onChange(ConfigMessages.route("orders", "/orders", "orders"));

        assertThat(match("/orders/42")).map(Route::id).contains("orders");
    }

    @Test
    @DisplayName("FR-12: every field of the route payload reaches the Route")
    void routePayloadIsMappedCompletely() {
        applier.onChange(ConfigMessages.change(EntityType.ROUTE, "products", new RouteConfig(
                "products", "/products", List.of("GET", "POST"), "products-service",
                "WEIGHTED", 2500, 3, "v2", 10, 7)));

        Route route = match("/products/1").orElseThrow();
        assertThat(route.serviceId()).isEqualTo("products-service");
        assertThat(route.methods()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
        assertThat(route.loadBalancing()).isEqualTo(LoadBalancingPolicy.WEIGHTED);
        assertThat(route.timeout()).isEqualTo(Duration.ofMillis(2500));
        assertThat(route.retry().maxAttempts()).isEqualTo(3);
        assertThat(route.canary().version()).isEqualTo("v2");
        assertThat(route.canary().percent()).isEqualTo(10);
        assertThat(route.version()).isEqualTo(7);
    }

    @Test
    @DisplayName("FR-12: a second change for the same route replaces the first")
    void laterChangeReplacesTheRoute() {
        applier.onChange(ConfigMessages.route("orders", "/orders", "orders"));
        applier.onChange(ConfigMessages.route("orders", "/orders", "orders-v2"));

        assertThat(match("/orders/42")).map(Route::serviceId).contains("orders-v2");
    }

    @Test
    @DisplayName("FR-12: a tombstone removes the route; other routes stay")
    void tombstoneRemovesTheRoute() {
        applier.onChange(ConfigMessages.route("orders", "/orders", "orders"));
        applier.onChange(ConfigMessages.route("users", "/users", "users"));

        applier.onChange(ConfigMessages.deleted(EntityType.ROUTE, "orders"));

        assertThat(match("/orders/42")).isEmpty();
        assertThat(match("/users/7")).isPresent();
    }

    @Test
    @DisplayName("FR-12: UPSTREAM changes add, replace and remove backend copies")
    void upstreamChangesAreApplied() {
        applier.onChange(ConfigMessages.upstream("orders-a", "orders", "http://orders-a:8081"));
        applier.onChange(ConfigMessages.upstream("orders-b", "orders", "http://orders-b:8081"));
        assertThat(upstreams.healthyInstances("orders"))
                .extracting(UpstreamInstance::id).containsExactlyInAnyOrder("orders-a", "orders-b");

        applier.onChange(ConfigMessages.deleted(EntityType.UPSTREAM, "orders-a"));

        assertThat(upstreams.healthyInstances("orders"))
                .extracting(UpstreamInstance::id).containsExactly("orders-b");
    }

    @Test
    @DisplayName("an invalid route payload is skipped and the previous table stays in use")
    void invalidPayloadKeepsThePreviousTable() {
        applier.onChange(ConfigMessages.route("orders", "/orders", "orders"));

        applier.onChange(ConfigMessages.change(EntityType.ROUTE, "broken", new RouteConfig(
                "broken", "no-leading-slash", List.of(), "orders", "ROUND_ROBIN", 5000, 1,
                null, null, 1)));
        applier.onChange(new ConfigChange(
                EntityType.ROUTE, "garbage", 1, "{not json", Instant.now()));

        assertThat(match("/orders/42")).isPresent();
    }

    @Test
    @DisplayName("changes for other entity types are ignored")
    void otherEntityTypesAreIgnored() {
        applier.onChange(ConfigMessages.apiKey("key-1", "hash-1", false));

        assertThat(match("/orders/42")).isEmpty();
        assertThat(upstreams.allInstances()).isEmpty();
    }

    private Optional<Route> match(String path) {
        return routes.match(new IncomingRequest(
                "req-1", HttpMethod.GET, path, Map.of(), null, "127.0.0.1", Instant.now()));
    }
}
