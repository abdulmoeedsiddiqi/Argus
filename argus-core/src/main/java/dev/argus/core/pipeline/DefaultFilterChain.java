package dev.argus.core.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * Chain of Responsibility: walks an ordered list of filters.
 *
 * <p>The chain is immutable. {@code proceed} does not move a shared position; it hands the
 * current filter a new chain that starts one step further. So one chain object can serve
 * every request on every virtual thread at the same time without locking.
 */
public final class DefaultFilterChain implements FilterChain {

    private final List<GatewayFilter> filters;
    private final int index;

    /** Creates a chain positioned at the first filter. The list must already be ordered. */
    public DefaultFilterChain(List<GatewayFilter> filters) {
        this(List.copyOf(Objects.requireNonNull(filters, "filters")), 0);
    }

    private DefaultFilterChain(List<GatewayFilter> filters, int index) {
        this.filters = filters;
        this.index = index;
    }

    @Override
    public FilterResult proceed(RequestContext context) {
        Objects.requireNonNull(context, "context");
        if (index >= filters.size()) {
            throw new IllegalStateException(
                    "no filter left to handle the request: the last filter must answer, not proceed");
        }
        GatewayFilter current = filters.get(index);
        return current.filter(context, new DefaultFilterChain(filters, index + 1));
    }
}
