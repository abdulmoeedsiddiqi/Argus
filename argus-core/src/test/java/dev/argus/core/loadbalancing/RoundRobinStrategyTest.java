package dev.argus.core.loadbalancing;

import static dev.argus.core.pipeline.PipelineFixtures.getOrder42;
import static dev.argus.core.routing.RoutingFixtures.instance;
import static org.assertj.core.api.Assertions.assertThat;

import dev.argus.core.model.UpstreamInstance;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** FR-02 round-robin strategy. */
class RoundRobinStrategyTest {

    private static final List<UpstreamInstance> FOUR = List.of(
            instance("orders-a", "orders"), instance("orders-b", "orders"),
            instance("orders-c", "orders"), instance("orders-d", "orders"));

    @Test
    @DisplayName("TC-02.1: 1,000 picks over 4 instances give exactly 250 each")
    void spreadsPicksEvenly() {
        RoundRobinStrategy strategy = new RoundRobinStrategy();
        Map<String, AtomicInteger> picks = new ConcurrentHashMap<>();

        for (int i = 0; i < 1_000; i++) {
            pickAndCount(strategy, picks);
        }

        assertThat(picks).hasSize(4);
        picks.values().forEach(count -> assertThat(count.get()).isEqualTo(250));
    }

    @Test
    @DisplayName("picks follow the list order, then start again")
    void picksInTurn() {
        RoundRobinStrategy strategy = new RoundRobinStrategy();
        List<UpstreamInstance> two = FOUR.subList(0, 2);

        assertThat(strategy.select(two, getOrder42())).contains(two.get(0));
        assertThat(strategy.select(two, getOrder42())).contains(two.get(1));
        assertThat(strategy.select(two, getOrder42())).contains(two.get(0));
    }

    @Test
    @DisplayName("no healthy instance means no pick")
    void emptyListGivesNoPick() {
        assertThat(new RoundRobinStrategy().select(List.of(), getOrder42())).isEmpty();
    }

    @Test
    @DisplayName("the position counter may overflow without producing an invalid pick")
    void survivesCounterOverflow() {
        RoundRobinStrategy strategy = new RoundRobinStrategy(Integer.MAX_VALUE);

        for (int i = 0; i < 5; i++) {
            assertThat(strategy.select(FOUR, getOrder42())).isPresent();
        }
    }

    @RepeatedTest(10)
    @DisplayName("TC-02.4: 1,000 concurrent picks on virtual threads still give exactly 250 each")
    void isThreadSafe() {
        RoundRobinStrategy strategy = new RoundRobinStrategy();
        Map<String, AtomicInteger> picks = new ConcurrentHashMap<>();

        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 1_000; i++) {
                threads.submit(() -> pickAndCount(strategy, picks));
            }
        }

        picks.values().forEach(count -> assertThat(count.get()).isEqualTo(250));
    }

    private static void pickAndCount(RoundRobinStrategy strategy, Map<String, AtomicInteger> picks) {
        UpstreamInstance picked = strategy.select(FOUR, getOrder42()).orElseThrow();
        picks.computeIfAbsent(picked.id(), id -> new AtomicInteger()).incrementAndGet();
    }
}
