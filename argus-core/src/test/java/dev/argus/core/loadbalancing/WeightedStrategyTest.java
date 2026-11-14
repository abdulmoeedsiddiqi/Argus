package dev.argus.core.loadbalancing;

import static dev.argus.core.pipeline.PipelineFixtures.getOrder42;
import static dev.argus.core.routing.RoutingFixtures.instance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.argus.core.model.UpstreamInstance;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FR-02 weighted strategy: an instance is picked in proportion to its weight. */
class WeightedStrategyTest {

    private static final UpstreamInstance HEAVY = instance("orders-a", "orders", 9, "v1");
    private static final UpstreamInstance LIGHT = instance("orders-b", "orders", 1, "v1");

    @Test
    @DisplayName("TC-02.2: weights 9 and 1 give a 90/10 split within 3 percentage points")
    void splitsTrafficByWeight() {
        Random seeded = new Random(42);
        WeightedStrategy strategy = new WeightedStrategy(seeded::nextInt);
        int picksOfHeavy = 0;
        int total = 10_000;

        for (int i = 0; i < total; i++) {
            if (strategy.select(List.of(HEAVY, LIGHT), getOrder42()).orElseThrow().equals(HEAVY)) {
                picksOfHeavy++;
            }
        }

        assertThat(100.0 * picksOfHeavy / total).isCloseTo(90.0, within(3.0));
    }

    @Test
    @DisplayName("each weight unit is one slot: slots 0..8 pick the weight-9 instance, slot 9 the other")
    void mapsRandomSlotsToInstances() {
        for (int slot = 0; slot <= 8; slot++) {
            assertThat(pickWithSlot(slot)).isEqualTo(HEAVY);
        }
        assertThat(pickWithSlot(9)).isEqualTo(LIGHT);
    }

    @Test
    @DisplayName("the random slot is drawn below the total weight")
    void drawsBelowTotalWeight() {
        int[] seenBound = new int[1];
        WeightedStrategy strategy = new WeightedStrategy(bound -> {
            seenBound[0] = bound;
            return 0;
        });

        strategy.select(List.of(HEAVY, LIGHT), getOrder42());

        assertThat(seenBound[0]).isEqualTo(10);
    }

    @Test
    @DisplayName("no healthy instance means no pick")
    void emptyListGivesNoPick() {
        assertThat(new WeightedStrategy().select(List.of(), getOrder42())).isEmpty();
    }

    @Test
    @DisplayName("the default strategy only ever picks from the given instances")
    void defaultRandomPicksFromTheList() {
        WeightedStrategy strategy = new WeightedStrategy();

        for (int i = 0; i < 100; i++) {
            assertThat(strategy.select(List.of(HEAVY, LIGHT), getOrder42()).orElseThrow())
                    .isIn(HEAVY, LIGHT);
        }
    }

    private static UpstreamInstance pickWithSlot(int slot) {
        return new WeightedStrategy(bound -> slot)
                .select(List.of(HEAVY, LIGHT), getOrder42()).orElseThrow();
    }
}
