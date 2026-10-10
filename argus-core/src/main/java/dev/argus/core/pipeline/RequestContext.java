package dev.argus.core.pipeline;

import dev.argus.core.auth.Principal;
import dev.argus.core.routing.Route;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-request state that flows through the filter chain.
 * Filters never mutate a context; they derive a new one with the "with" methods,
 * so a context can never be observed half-updated.
 */
public record RequestContext(IncomingRequest request, Principal principal, Route route) {

    public RequestContext {
        Objects.requireNonNull(request, "request");
    }

    public static RequestContext of(IncomingRequest request) {
        return new RequestContext(request, null, null);
    }

    public RequestContext withPrincipal(Principal newPrincipal) {
        return new RequestContext(request, Objects.requireNonNull(newPrincipal), route);
    }

    public RequestContext withRoute(Route newRoute) {
        return new RequestContext(request, principal, Objects.requireNonNull(newRoute));
    }

    public Optional<Principal> authenticatedPrincipal() {
        return Optional.ofNullable(principal);
    }

    public Optional<Route> matchedRoute() {
        return Optional.ofNullable(route);
    }
}
