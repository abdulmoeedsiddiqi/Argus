package dev.argus.core.pipeline;

import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.Principal;
import dev.argus.core.model.Route;
import dev.argus.core.model.UpstreamInstance;
import java.util.Objects;
import java.util.Optional;

/**
 * Per-request state that flows through the filter chain.
 * Filters never mutate a context; they derive a new one with the "with" methods,
 * so a context can never be observed half-updated.
 *
 * <p>{@code upstream} is the instance chosen by the routing filter; the proxy filter sends the
 * request to it.
 */
public record RequestContext(
        IncomingRequest request, Principal principal, Route route, UpstreamInstance upstream) {

    public RequestContext {
        Objects.requireNonNull(request, "request");
    }

    public static RequestContext of(IncomingRequest request) {
        return new RequestContext(request, null, null, null);
    }

    public RequestContext withPrincipal(Principal newPrincipal) {
        return new RequestContext(request, Objects.requireNonNull(newPrincipal), route, upstream);
    }

    public RequestContext withRoute(Route newRoute) {
        return new RequestContext(request, principal, Objects.requireNonNull(newRoute), upstream);
    }

    public RequestContext withUpstream(UpstreamInstance newUpstream) {
        return new RequestContext(request, principal, route, Objects.requireNonNull(newUpstream));
    }

    public Optional<Principal> authenticatedPrincipal() {
        return Optional.ofNullable(principal);
    }

    public Optional<Route> matchedRoute() {
        return Optional.ofNullable(route);
    }

    public Optional<UpstreamInstance> selectedUpstream() {
        return Optional.ofNullable(upstream);
    }
}
