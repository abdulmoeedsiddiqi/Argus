package dev.argus.core.loadbalancing;

import static dev.argus.core.pipeline.PipelineFixtures.getOrder42;
import static dev.argus.core.routing.RoutingFixtures.instance;
import static org.assertj.core.api.Assertions.assertThat;

import dev.argus.core.model.UpstreamInstance;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** FR-02 least-connections strategy: the instance with the fewest requests in flight wins. */
class LeastConnectionsStrategyTest {

    private static final UpstreamInstance A = instance("users-a", "users");
    private static final UpstreamInstance B = instance("users-b", "users");
    private static final List<UpstreamInstance> BOTH = List.of(A, B);

    private final LeastConnectionsStrategy strategy = new LeastConnectionsStrategy();

    @Test
    @DisplayName("TC-02.3: the instance with fewer requests in flight is picked")
    void picksTheLeastBusyInstance() {
        strategy.started(A);
        strategy.started(A);
        strategy.started(B);

        assertThat(strategy.select(BOTH, getOrder42())).contains(B);
    }

    @Test
    @DisplayName("TC-02.3: when a request finishes, its instance becomes less busy again")
    void finishedRequestsFreeTheInstance() {
        strategy.started(A);
        strategy.started(B);
        strategy.started(B);
        assertThat(strategy.select(BOTH, getOrder42())).contains(A);

        strategy.finished(B);
        strategy.finished(B);

        assertThat(strategy.select(BOTH, getOrder42())).contains(B);
        assertThat(strategy.activeRequests(B)).isZero();
    }

    @Test
    @DisplayName("with equal load the first instance in the list is picked")
    void tieGoesToTheFirstInstance() {
        assertThat(strategy.select(BOTH, getOrder42())).contains(A);
    }

    @Test
    @DisplayName("an instance that was never used counts as idle")
    void unknownInstanceIsIdle() {
        assertThat(strategy.activeRequests(A)).isZero();
    }

    @Test
    @DisplayName("no healthy instance means no pick")
    void emptyListGivesNoPick() {
        assertThat(strategy.select(List.of(), getOrder42())).isEmpty();
    }

    @RepeatedTest(10)
    @DisplayName("TC-02.4: concurrent start and finish calls leave no leaked count")
    void countsAreThreadSafe() {
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 1_000; i++) {
                threads.submit(() -> {
                    UpstreamInstance picked = strategy.select(BOTH, getOrder42()).orElseThrow();
                    strategy.started(picked);
                    try {
                        Thread.yield();
                    } finally {
                        strategy.finished(picked);
                    }
                });
            }
        }

        assertThat(strategy.activeRequests(A)).isZero();
        assertThat(strategy.activeRequests(B)).isZero();
    }
}
