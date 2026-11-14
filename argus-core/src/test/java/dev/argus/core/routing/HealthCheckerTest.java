package dev.argus.core.routing;

import static dev.argus.core.routing.RoutingFixtures.instance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.argus.core.model.UpstreamInstance;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-03 active health checks: an instance leaves rotation after failed checks and rejoins
 * after it recovers. The probe is a fake, and checkAll() is called directly, so the tests
 * need no real backend and no sleeping.
 */
class HealthCheckerTest {

    private static final int FAILURE_THRESHOLD = 3;
    private static final UpstreamInstance ORDERS_A = instance("orders-a", "orders");
    private static final UpstreamInstance ORDERS_B = instance("orders-b", "orders");

    private final InMemoryUpstreamRegistry registry = new InMemoryUpstreamRegistry();
    /** Ids of instances whose probe currently fails. */
    private final Set<String> failing = ConcurrentHashMap.newKeySet();
    private final HealthProbe probe = target -> !failing.contains(target.id());
    private HealthChecker checker;

    @BeforeEach
    void setUp() {
        registry.replaceAll(List.of(ORDERS_A, ORDERS_B));
        checker = new HealthChecker(registry::allInstances, probe, registry, FAILURE_THRESHOLD);
    }

    @Test
    @DisplayName("TC-03.1: an instance is removed from rotation after 3 failed checks in a row")
    void failedChecksRemoveTheInstance() {
        failing.add("orders-a");

        checker.checkAll();
        checker.checkAll();
        assertThat(registry.healthyInstances("orders")).containsExactly(ORDERS_A, ORDERS_B);

        checker.checkAll();
        assertThat(registry.healthyInstances("orders")).containsExactly(ORDERS_B);
    }

    @Test
    @DisplayName("TC-03.2: a removed instance rejoins rotation after one successful check")
    void recoveredInstanceRejoins() {
        failing.add("orders-a");
        checker.checkAll();
        checker.checkAll();
        checker.checkAll();

        failing.remove("orders-a");
        checker.checkAll();

        assertThat(registry.healthyInstances("orders")).containsExactly(ORDERS_A, ORDERS_B);
    }

    @Test
    @DisplayName("TC-03.1: a success resets the count, so failures must be consecutive")
    void successResetsTheFailureCount() {
        failing.add("orders-a");
        checker.checkAll();
        checker.checkAll();
        failing.remove("orders-a");
        checker.checkAll();
        failing.add("orders-a");
        checker.checkAll();
        checker.checkAll();

        assertThat(registry.healthyInstances("orders")).containsExactly(ORDERS_A, ORDERS_B);
    }

    @Test
    @DisplayName("TC-03.1: a probe that throws counts as a failed check and does not stop the others")
    void probeExceptionCountsAsFailure() {
        HealthProbe throwing = target -> {
            if (target.id().equals("orders-a")) {
                throw new IllegalStateException("connection refused");
            }
            return true;
        };
        HealthChecker throwingChecker =
                new HealthChecker(registry::allInstances, throwing, registry, 1);

        throwingChecker.checkAll();

        assertThat(registry.healthyInstances("orders")).containsExactly(ORDERS_B);
    }

    @Test
    @DisplayName("a failure threshold below 1 is rejected")
    void rejectsInvalidThreshold() {
        assertThatThrownBy(() -> new HealthChecker(registry::allInstances, probe, registry, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start runs the checks periodically until stop is called")
    void startRunsChecksPeriodically() throws InterruptedException {
        CountDownLatch threeRounds = new CountDownLatch(3);
        HealthProbe counting = target -> {
            if (target.id().equals("orders-a")) {
                threeRounds.countDown();
            }
            return true;
        };
        HealthChecker periodic = new HealthChecker(registry::allInstances, counting, registry, 1);

        periodic.start(Duration.ofMillis(10));
        try {
            assertThat(threeRounds.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            periodic.stop();
        }
    }
}
