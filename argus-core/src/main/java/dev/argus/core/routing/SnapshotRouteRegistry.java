package dev.argus.core.routing;

import dev.argus.core.model.HttpMethod;
import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.Route;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Route table of one gateway instance (FR-01).
 *
 * <p>Thread-safety: the routes are kept in an immutable {@link RouteTable} behind an
 * {@link AtomicReference}. A request reads the reference once and works on that table, so it
 * never blocks and never sees a half-applied update. A configuration change builds a complete
 * new table and swaps it in with one atomic write.
 */
public final class SnapshotRouteRegistry implements RouteRegistry {

    private final AtomicReference<RouteTable> snapshot = new AtomicReference<>(RouteTable.EMPTY);

    @Override
    public Optional<Route> match(IncomingRequest request) {
        return snapshot.get().match(request.method(), pathWithoutQuery(request.path()));
    }

    @Override
    public void replaceAll(Collection<Route> routes) {
        // The new table is fully built (and validated) before it becomes visible.
        snapshot.set(RouteTable.of(routes));
    }

    private static String pathWithoutQuery(String path) {
        int queryStart = path.indexOf('?');
        return queryStart < 0 ? path : path.substring(0, queryStart);
    }

    /** Immutable list of routes, longest path prefix first, so the first match is the best one. */
    private record RouteTable(List<Route> routes) {

        static final RouteTable EMPTY = new RouteTable(List.of());

        static RouteTable of(Collection<Route> routes) {
            Objects.requireNonNull(routes, "routes");
            Set<String> prefixes = new HashSet<>();
            for (Route route : routes) {
                if (!prefixes.add(route.pathPrefix())) {
                    throw new IllegalArgumentException(
                            "two routes share the path prefix " + route.pathPrefix());
                }
            }
            List<Route> longestPrefixFirst = routes.stream()
                    .sorted(Comparator.comparingInt((Route route) -> route.pathPrefix().length())
                            .reversed())
                    .toList();
            return new RouteTable(longestPrefixFirst);
        }

        Optional<Route> match(HttpMethod method, String path) {
            for (Route route : routes) {
                if (coversPath(route.pathPrefix(), path) && acceptsMethod(route, method)) {
                    return Optional.of(route);
                }
            }
            return Optional.empty();
        }

        /** "/orders" covers "/orders" and "/orders/42", but not "/ordersX". */
        private static boolean coversPath(String prefix, String path) {
            if (!path.startsWith(prefix)) {
                return false;
            }
            return path.length() == prefix.length()
                    || prefix.endsWith("/")
                    || path.charAt(prefix.length()) == '/';
        }

        /** A route that lists no methods accepts every method. */
        private static boolean acceptsMethod(Route route, HttpMethod method) {
            return route.methods().isEmpty() || route.methods().contains(method);
        }
    }
}
