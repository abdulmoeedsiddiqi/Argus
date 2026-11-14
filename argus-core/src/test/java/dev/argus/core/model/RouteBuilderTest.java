package dev.argus.core.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Builder pattern (SE423 report, Section 7.1): defaults, each validation rule, and the
 * immutability of built routes. Like the records in this package, the builder reports invalid
 * values with IllegalArgumentException, so the model package keeps depending on the JDK only.
 */
class RouteBuilderTest {

    @Test
    @DisplayName("a route with only a path and a service gets sensible defaults")
    void fillsInDefaults() {
        Route route = Route.builder().pathPrefix("/orders").service("orders").build();

        assertThat(route.id()).isEqualTo("orders");
        assertThat(route.pathPrefix()).isEqualTo("/orders");
        assertThat(route.serviceId()).isEqualTo("orders");
        assertThat(route.methods()).isEmpty();
        assertThat(route.loadBalancing()).isEqualTo(LoadBalancingPolicy.ROUND_ROBIN);
        assertThat(route.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(route.retry()).isEqualTo(RetrySpec.noRetry());
        assertThat(route.rateLimit()).isNull();
        assertThat(route.canary()).isNull();
        assertThat(route.version()).isZero();
    }

    @Test
    @DisplayName("every optional part can be set step by step")
    void setsEveryPart() {
        RateLimitPolicy limit = new RateLimitPolicy(10, Duration.ofMinutes(1));
        RetrySpec retry = new RetrySpec(3, Duration.ofMillis(100), 2.0);

        Route route = Route.builder()
                .id("orders-route")
                .pathPrefix("/orders")
                .methods(HttpMethod.GET, HttpMethod.POST)
                .service("orders")
                .loadBalancing(LoadBalancingPolicy.WEIGHTED)
                .timeout(Duration.ofSeconds(2))
                .retry(retry)
                .rateLimit(limit)
                .canary("v2", 10)
                .version(7)
                .build();

        assertThat(route.id()).isEqualTo("orders-route");
        assertThat(route.methods()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
        assertThat(route.loadBalancing()).isEqualTo(LoadBalancingPolicy.WEIGHTED);
        assertThat(route.timeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(route.retry()).isEqualTo(retry);
        assertThat(route.rateLimit()).isEqualTo(limit);
        assertThat(route.canary()).isEqualTo(new CanarySplit("v2", 10));
        assertThat(route.version()).isEqualTo(7);
    }

    @Test
    @DisplayName("a built route is immutable: its method set cannot be changed")
    void builtRouteIsImmutable() {
        Route route = Route.builder().pathPrefix("/orders").service("orders")
                .methods(HttpMethod.GET).build();

        assertThatThrownBy(() -> route.methods().add(HttpMethod.POST))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("a route without a path prefix is rejected")
    void rejectsMissingPath() {
        assertThatThrownBy(() -> Route.builder().service("orders").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pathPrefix");
    }

    @Test
    @DisplayName("a route without a service is rejected")
    void rejectsMissingService() {
        assertThatThrownBy(() -> Route.builder().pathPrefix("/orders").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("service");
    }

    @Test
    @DisplayName("a path prefix must start with a slash")
    void rejectsPathWithoutLeadingSlash() {
        assertThatThrownBy(() -> Route.builder().pathPrefix("orders").service("orders").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "timeout {0} ms is accepted")
    @ValueSource(longs = {100, 101, 59_999, 60_000})
    @DisplayName("BVA: timeouts on and just inside the 100 ms..60 s limits are accepted")
    void acceptsTimeoutsInsideTheLimits(long millis) {
        Route route = Route.builder().pathPrefix("/orders").service("orders")
                .timeout(Duration.ofMillis(millis)).build();

        assertThat(route.timeout()).isEqualTo(Duration.ofMillis(millis));
    }

    @ParameterizedTest(name = "timeout {0} ms is rejected")
    @ValueSource(longs = {0, 99, 60_001})
    @DisplayName("BVA: timeouts just outside the 100 ms..60 s limits are rejected")
    void rejectsTimeoutsOutsideTheLimits(long millis) {
        assertThatThrownBy(() -> Route.builder().pathPrefix("/orders").service("orders")
                .timeout(Duration.ofMillis(millis)).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
    }

    @ParameterizedTest(name = "canary {0}% is rejected")
    @ValueSource(ints = {-1, 101})
    @DisplayName("TC-04.2: a canary percentage outside 0..100 is rejected")
    void rejectsInvalidCanaryPercent(int percent) {
        assertThatThrownBy(() -> Route.builder().pathPrefix("/products").service("products")
                .canary("v2", percent).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canary");
    }
}
