package dev.argus.core.routing;

import static dev.argus.core.routing.RoutingFixtures.instance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.argus.core.model.HealthStatus;
import dev.argus.core.model.UpstreamInstance;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FR-03 / QR-01: the registry knows the instances of each service and which are healthy. */
class InMemoryUpstreamRegistryTest {

    private static final UpstreamInstance ORDERS_A = instance("orders-a", "orders");
    private static final UpstreamInstance ORDERS_B = instance("orders-b", "orders");
    private static final UpstreamInstance USERS_A = instance("users-a", "users");

    private final InMemoryUpstreamRegistry registry = new InMemoryUpstreamRegistry();

    @BeforeEach
    void register() {
        registry.replaceAll(List.of(ORDERS_A, ORDERS_B, USERS_A));
    }

    @Test
    @DisplayName("instances are listed per service, in registration order")
    void listsInstancesPerService() {
        assertThat(registry.healthyInstances("orders")).containsExactly(ORDERS_A, ORDERS_B);
        assertThat(registry.healthyInstances("users")).containsExactly(USERS_A);
    }

    @Test
    @DisplayName("an unknown service has no instances")
    void unknownServiceHasNoInstances() {
        assertThat(registry.healthyInstances("payments")).isEmpty();
    }

    @Test
    @DisplayName("an instance that has not been checked yet (UNKNOWN) receives traffic")
    void uncheckedInstanceIsEligible() {
        assertThat(registry.healthOf("orders-a")).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(registry.healthyInstances("orders")).contains(ORDERS_A);
    }

    @Test
    @DisplayName("TC-Q01.1: an instance marked UNHEALTHY is never offered for selection")
    void unhealthyInstanceIsLeftOut() {
        registry.updateHealth("orders-a", HealthStatus.UNHEALTHY);

        assertThat(registry.healthyInstances("orders")).containsExactly(ORDERS_B);
    }

    @Test
    @DisplayName("TC-03.2: an instance marked HEALTHY again is offered again")
    void recoveredInstanceIsOfferedAgain() {
        registry.updateHealth("orders-a", HealthStatus.UNHEALTHY);
        registry.updateHealth("orders-a", HealthStatus.HEALTHY);

        assertThat(registry.healthyInstances("orders")).containsExactly(ORDERS_A, ORDERS_B);
    }

    @Test
    @DisplayName("a health report for an unknown instance is ignored")
    void ignoresHealthOfUnknownInstance() {
        registry.updateHealth("ghost", HealthStatus.UNHEALTHY);

        assertThat(registry.healthOf("ghost")).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(registry.healthyInstances("orders")).containsExactly(ORDERS_A, ORDERS_B);
    }

    @Test
    @DisplayName("replaceAll keeps the health of instances that stay and forgets removed ones")
    void replaceAllKeepsHealthOfRemainingInstances() {
        registry.updateHealth("orders-a", HealthStatus.UNHEALTHY);
        registry.updateHealth("orders-b", HealthStatus.UNHEALTHY);

        registry.replaceAll(List.of(ORDERS_A, USERS_A));

        assertThat(registry.healthOf("orders-a")).isEqualTo(HealthStatus.UNHEALTHY);
        assertThat(registry.healthOf("orders-b")).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(registry.allInstances()).containsExactly(ORDERS_A, USERS_A);
    }

    @Test
    @DisplayName("two instances with the same id are rejected")
    void rejectsDuplicateInstanceIds() {
        UpstreamInstance duplicate = instance("orders-a", "orders");

        assertThatThrownBy(() -> registry.replaceAll(List.of(ORDERS_A, duplicate)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the returned list cannot be changed by the caller")
    void returnedListIsImmutable() {
        List<UpstreamInstance> orders = registry.healthyInstances("orders");

        assertThatThrownBy(() -> orders.add(USERS_A))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
