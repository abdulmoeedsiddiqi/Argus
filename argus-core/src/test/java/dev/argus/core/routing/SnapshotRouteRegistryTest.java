package dev.argus.core.routing;

import static dev.argus.core.routing.RoutingFixtures.get;
import static dev.argus.core.routing.RoutingFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.argus.core.model.HttpMethod;
import dev.argus.core.model.Route;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** FR-01 route matching: by path prefix and HTTP method, on an atomically replaced snapshot. */
class SnapshotRouteRegistryTest {

    private static final Route ORDERS = Route.builder().pathPrefix("/orders").service("orders").build();
    private static final Route USERS = Route.builder().pathPrefix("/users").service("users").build();

    private final SnapshotRouteRegistry registry = new SnapshotRouteRegistry();

    @Test
    @DisplayName("TC-01.1: a request under the path prefix of a route matches that route")
    void matchesByPathPrefix() {
        registry.replaceAll(List.of(ORDERS, USERS));

        assertThat(registry.match(get("/orders/42"))).contains(ORDERS);
        assertThat(registry.match(get("/orders"))).contains(ORDERS);
        assertThat(registry.match(get("/users/7"))).contains(USERS);
    }

    @Test
    @DisplayName("TC-01.1: the query string does not take part in matching")
    void ignoresQueryString() {
        registry.replaceAll(List.of(ORDERS));

        assertThat(registry.match(get("/orders/42?expand=items"))).contains(ORDERS);
        assertThat(registry.match(get("/orders?page=2"))).contains(ORDERS);
    }

    @Test
    @DisplayName("TC-01.2: an unknown path matches no route")
    void unknownPathMatchesNothing() {
        registry.replaceAll(List.of(ORDERS));

        assertThat(registry.match(get("/payments/1"))).isEmpty();
        assertThat(registry.match(get("/"))).isEmpty();
    }

    @Test
    @DisplayName("TC-01.2: a prefix matches whole path segments only (/orders does not match /ordersX)")
    void prefixMatchesWholeSegmentsOnly() {
        registry.replaceAll(List.of(ORDERS));

        assertThat(registry.match(get("/ordersX"))).isEmpty();
        assertThat(registry.match(get("/orders-archive/1"))).isEmpty();
    }

    @Test
    @DisplayName("TC-01.3: a method the route does not list matches no route")
    void wrongMethodMatchesNothing() {
        Route readOnly = Route.builder().pathPrefix("/orders").service("orders")
                .methods(HttpMethod.GET).build();
        registry.replaceAll(List.of(readOnly));

        assertThat(registry.match(request(HttpMethod.GET, "/orders/42"))).contains(readOnly);
        assertThat(registry.match(request(HttpMethod.POST, "/orders"))).isEmpty();
    }

    @Test
    @DisplayName("a route that lists no methods accepts every method")
    void routeWithoutMethodsAcceptsAll() {
        registry.replaceAll(List.of(ORDERS));

        for (HttpMethod method : HttpMethod.values()) {
            assertThat(registry.match(request(method, "/orders/42"))).contains(ORDERS);
        }
    }

    @Test
    @DisplayName("the longest matching prefix wins, whatever order routes were given in")
    void longestPrefixWins() {
        Route archive = Route.builder().id("archive").pathPrefix("/orders/archive")
                .service("orders-archive").build();
        registry.replaceAll(List.of(ORDERS, archive));

        assertThat(registry.match(get("/orders/archive/2025"))).contains(archive);
        assertThat(registry.match(get("/orders/42"))).contains(ORDERS);
    }

    @Test
    @DisplayName("a new registry has no routes")
    void startsEmpty() {
        assertThat(registry.match(get("/orders/42"))).isEmpty();
    }

    @Test
    @DisplayName("replaceAll swaps the whole table: old routes disappear, new ones appear")
    void replaceAllSwapsTheWholeTable() {
        registry.replaceAll(List.of(ORDERS));
        registry.replaceAll(List.of(USERS));

        assertThat(registry.match(get("/orders/42"))).isEmpty();
        assertThat(registry.match(get("/users/7"))).contains(USERS);
    }

    @Test
    @DisplayName("two routes with the same path prefix are rejected and the old table is kept")
    void rejectsDuplicatePathPrefix() {
        registry.replaceAll(List.of(ORDERS));
        Route duplicate = Route.builder().id("other").pathPrefix("/orders").service("other").build();

        assertThatThrownBy(() -> registry.replaceAll(List.of(USERS, ORDERS, duplicate)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(registry.match(get("/orders/42"))).contains(ORDERS);
        assertThat(registry.match(get("/users/7"))).isEmpty();
    }

    @RepeatedTest(5)
    @DisplayName("readers never see a half-applied update while the table is being replaced")
    void readersNeverSeeAPartialUpdate() throws Exception {
        Route a = Route.builder().pathPrefix("/a").service("a").build();
        Route b = Route.builder().pathPrefix("/b").service("b").build();
        registry.replaceAll(List.of(a, b));
        AtomicBoolean stop = new AtomicBoolean();
        AtomicBoolean sawEmptyTable = new AtomicBoolean();

        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> writer = threads.submit(() -> {
                // Both tables contain /a, so a reader must find it at every moment.
                for (int i = 0; i < 2_000; i++) {
                    registry.replaceAll(i % 2 == 0 ? List.of(a) : List.of(a, b));
                }
                stop.set(true);
            });
            for (int reader = 0; reader < 8; reader++) {
                threads.submit(() -> {
                    while (!stop.get()) {
                        if (registry.match(get("/a")).isEmpty()) {
                            sawEmptyTable.set(true);
                        }
                    }
                });
            }
            writer.get();
        }

        assertThat(sawEmptyTable).isFalse();
    }
}
