package dev.argus.core.loadbalancing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.argus.core.model.LoadBalancingPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Factory Method (SE423 report, Section 7.2): every policy maps to the expected strategy and
 * unknown policies are rejected.
 */
class DefaultLoadBalancingStrategyFactoryTest {

    private final LoadBalancingStrategyFactory factory = new DefaultLoadBalancingStrategyFactory();

    @Test
    @DisplayName("FR-02: each configured policy creates its strategy")
    void mapsEachPolicyToItsStrategy() {
        assertThat(factory.create(LoadBalancingPolicy.ROUND_ROBIN))
                .isInstanceOf(RoundRobinStrategy.class);
        assertThat(factory.create(LoadBalancingPolicy.WEIGHTED))
                .isInstanceOf(WeightedStrategy.class);
        assertThat(factory.create(LoadBalancingPolicy.LEAST_CONNECTIONS))
                .isInstanceOf(LeastConnectionsStrategy.class);
    }

    @ParameterizedTest
    @EnumSource(LoadBalancingPolicy.class)
    @DisplayName("no policy is left without a strategy")
    void everyPolicyIsCovered(LoadBalancingPolicy policy) {
        assertThat(factory.create(policy)).isNotNull();
    }

    @Test
    @DisplayName("each call creates a new strategy, so routes never share a position or counts")
    void createsAFreshStrategyEachTime() {
        assertThat(factory.create(LoadBalancingPolicy.ROUND_ROBIN))
                .isNotSameAs(factory.create(LoadBalancingPolicy.ROUND_ROBIN));
    }

    @Test
    @DisplayName("a missing policy is rejected")
    void rejectsMissingPolicy() {
        assertThatThrownBy(() -> factory.create(null)).isInstanceOf(NullPointerException.class);
    }
}
