package dev.argus.core.pipeline;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Factory Method: builds the request pipeline from the registered filters. Callers get a
 * {@link FilterChain} and never see the concrete chain class or care about registration order.
 */
public final class FilterChainFactory {

    /** Returns a chain that runs the filters by ascending {@link GatewayFilter#order()}. */
    public FilterChain create(List<GatewayFilter> filters) {
        Objects.requireNonNull(filters, "filters");
        if (filters.isEmpty()) {
            throw new IllegalArgumentException("a filter chain needs at least one filter");
        }
        List<GatewayFilter> ordered = filters.stream()
                .sorted(Comparator.comparingInt(GatewayFilter::order))
                .toList();
        return new DefaultFilterChain(ordered);
    }
}
