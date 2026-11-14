package dev.argus.core.pipeline;

import static dev.argus.core.pipeline.PipelineFixtures.answering;
import static dev.argus.core.pipeline.PipelineFixtures.forwarded;
import static dev.argus.core.pipeline.PipelineFixtures.getOrder42;
import static dev.argus.core.pipeline.PipelineFixtures.passing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Factory Method (SE423 report, Section 7.2): the chain is built in the right order. */
class FilterChainFactoryTest {

    private final FilterChainFactory factory = new FilterChainFactory();
    private final List<String> log = new ArrayList<>();

    @Test
    @DisplayName("filters are ordered by order(), whatever order they are registered in")
    void ordersFiltersByOrderValue() {
        FilterChain chain = factory.create(List.of(
                answering("proxy", 500, log, forwarded("ok")),
                passing("authentication", 100, log),
                passing("routing", 400, log)));

        chain.proceed(getOrder42());

        assertThat(log).containsExactly("authentication", "routing", "proxy");
    }

    @Test
    @DisplayName("a chain with no filters is rejected: nothing could answer the request")
    void rejectsEmptyFilterList() {
        assertThatThrownBy(() -> factory.create(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a null filter list is rejected")
    void rejectsNullFilterList() {
        assertThatThrownBy(() -> factory.create(null))
                .isInstanceOf(NullPointerException.class);
    }
}
