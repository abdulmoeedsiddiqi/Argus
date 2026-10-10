package dev.argus.core.routing;

import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.Route;
import java.util.Collection;
import java.util.Optional;

/**
 * Read side: finds the route for a request. Write side: atomically replaces the route set
 * when configuration changes arrive. Implementations hold an immutable snapshot behind an
 * AtomicReference, so readers never block and never see a partially applied update.
 */
public interface RouteRegistry {

    Optional<Route> match(IncomingRequest request);

    void replaceAll(Collection<Route> routes);
}
