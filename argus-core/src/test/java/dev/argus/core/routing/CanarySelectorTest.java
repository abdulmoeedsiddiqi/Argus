package dev.argus.core.routing;

import static dev.argus.core.routing.RoutingFixtures.instance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.argus.core.model.CanarySplit;
import dev.argus.core.model.UpstreamInstance;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * FR-04 canary routing: a configured percentage of a route's traffic goes to instances of the
 * canary version. The selector draws a number 0..99; draws below the percentage go canary.
 */
class CanarySelectorTest {

    private static final UpstreamInstance V1_A = instance("products-v1-a", "products", 1, "v1");
    private static final UpstreamInstance V1_B = instance("products-v1-b", "products", 1, "v1");
    private static final UpstreamInstance V2 = instance("products-v2", "products", 1, "v2");
    private static final List<UpstreamInstance> ALL = List.of(V1_A, V1_B, V2);

    @Test
    @DisplayName("TC-04.1: at 0% no draw ever reaches the canary version")
    void zeroPercentNeverGoesCanary() {
        CanarySplit split = new CanarySplit("v2", 0);

        for (int draw = 0; draw < 100; draw++) {
            assertThat(selectorDrawing(draw).filterByVersion(ALL, split)).containsExactly(V1_A, V1_B);
        }
    }

    @Test
    @DisplayName("TC-04.1: at 100% every draw reaches the canary version")
    void hundredPercentAlwaysGoesCanary() {
        CanarySplit split = new CanarySplit("v2", 100);

        for (int draw = 0; draw < 100; draw++) {
            assertThat(selectorDrawing(draw).filterByVersion(ALL, split)).containsExactly(V2);
        }
    }

    @Test
    @DisplayName("TC-04.1: at 1% only the lowest draw (0) reaches the canary version")
    void onePercentBoundary() {
        CanarySplit split = new CanarySplit("v2", 1);

        assertThat(selectorDrawing(0).filterByVersion(ALL, split)).containsExactly(V2);
        assertThat(selectorDrawing(1).filterByVersion(ALL, split)).containsExactly(V1_A, V1_B);
    }

    @Test
    @DisplayName("TC-04.1: at 99% only the highest draw (99) stays on the stable version")
    void ninetyNinePercentBoundary() {
        CanarySplit split = new CanarySplit("v2", 99);

        assertThat(selectorDrawing(98).filterByVersion(ALL, split)).containsExactly(V2);
        assertThat(selectorDrawing(99).filterByVersion(ALL, split)).containsExactly(V1_A, V1_B);
    }

    @Test
    @DisplayName("UC-08: a 10% canary receives about 10% of 10,000 requests")
    void tenPercentSplitOverManyRequests() {
        Random seeded = new Random(7);
        CanarySelector selector = new CanarySelector(seeded::nextInt);
        CanarySplit split = new CanarySplit("v2", 10);
        int canaryRequests = 0;
        int total = 10_000;

        for (int i = 0; i < total; i++) {
            if (selector.filterByVersion(ALL, split).contains(V2)) {
                canaryRequests++;
            }
        }

        assertThat(100.0 * canaryRequests / total).isCloseTo(10.0, within(3.0));
    }

    @Test
    @DisplayName("a route without a canary split keeps all instances")
    void noSplitKeepsAllInstances() {
        assertThat(selectorDrawing(0).filterByVersion(ALL, null)).isEqualTo(ALL);
    }

    @Test
    @DisplayName("if no canary instance is healthy, the request falls back to the stable version")
    void fallsBackToStableWhenCanaryIsGone() {
        CanarySplit split = new CanarySplit("v2", 100);

        assertThat(selectorDrawing(0).filterByVersion(List.of(V1_A, V1_B), split))
                .containsExactly(V1_A, V1_B);
    }

    @Test
    @DisplayName("if no stable instance is healthy, the request falls back to the canary version")
    void fallsBackToCanaryWhenStableIsGone() {
        CanarySplit split = new CanarySplit("v2", 0);

        assertThat(selectorDrawing(50).filterByVersion(List.of(V2), split)).containsExactly(V2);
    }

    @ParameterizedTest(name = "{0}% is rejected")
    @ValueSource(ints = {-1, 101})
    @DisplayName("TC-04.2: a canary percentage outside 0..100 cannot be configured")
    void invalidPercentIsRejected(int percent) {
        // Existing Milestone 1 validation in the CanarySplit record; this test documents it.
        assertThatThrownBy(() -> new CanarySplit("v2", percent))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CanarySelector selectorDrawing(int draw) {
        return new CanarySelector(bound -> draw);
    }
}
