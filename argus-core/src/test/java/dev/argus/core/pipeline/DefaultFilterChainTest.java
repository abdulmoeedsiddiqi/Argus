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

/**
 * Chain of Responsibility (SE423 report, Section 7.3): filters run in order, a rejecting
 * filter stops the chain, and the last filter is reached only when all checks pass.
 */
class DefaultFilterChainTest {

    private final List<String> log = new ArrayList<>();

    @Test
    @DisplayName("filters run in the given order and the last filter's result is returned")
    void runsFiltersInOrder() {
        FilterResult answer = forwarded("order 42");
        FilterChain chain = new DefaultFilterChain(List.of(
                passing("first", 100, log),
                passing("second", 200, log),
                answering("last", 500, log, answer)));

        FilterResult result = chain.proceed(getOrder42());

        assertThat(log).containsExactly("first", "second", "last");
        assertThat(result).isSameAs(answer);
    }

    @Test
    @DisplayName("a rejecting filter stops the chain: later filters never run")
    void rejectingFilterStopsTheChain() {
        FilterResult rejection = new FilterResult.Rejected(
                429, RejectionReason.RATE_LIMITED, "too many requests");
        FilterChain chain = new DefaultFilterChain(List.of(
                passing("first", 100, log),
                answering("rejecting", 300, log, rejection),
                answering("last", 500, log, forwarded("never sent"))));

        FilterResult result = chain.proceed(getOrder42());

        assertThat(log).containsExactly("first", "rejecting");
        assertThat(result).isSameAs(rejection);
    }

    @Test
    @DisplayName("the same chain can serve many requests: it keeps no position between requests")
    void chainIsReusableAcrossRequests() {
        FilterChain chain = new DefaultFilterChain(List.of(
                passing("first", 100, log),
                answering("last", 500, log, forwarded("ok"))));

        chain.proceed(getOrder42());
        chain.proceed(getOrder42());

        assertThat(log).containsExactly("first", "last", "first", "last");
    }

    @Test
    @DisplayName("proceeding past the last filter is a wiring error, not a silent success")
    void proceedingPastTheEndFails() {
        FilterChain chain = new DefaultFilterChain(List.of(passing("only", 100, log)));

        assertThatThrownBy(() -> chain.proceed(getOrder42()))
                .isInstanceOf(IllegalStateException.class);
    }
}
