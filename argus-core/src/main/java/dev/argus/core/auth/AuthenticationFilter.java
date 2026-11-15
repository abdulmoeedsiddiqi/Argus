package dev.argus.core.auth;

import dev.argus.core.exception.AuthenticationFailedException;
import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.Principal;
import dev.argus.core.pipeline.FilterChain;
import dev.argus.core.pipeline.FilterResult;
import dev.argus.core.pipeline.GatewayFilter;
import dev.argus.core.pipeline.RequestContext;
import java.util.List;
import java.util.Objects;

/**
 * First step of the chain (module M2): establishes who is calling. The request continues only
 * with a valid credential; the caller is then available to later filters as a {@link Principal}.
 *
 * <p>The filter does not know how a credential is checked. It asks each {@link Authenticator}
 * whether it supports the request and lets the first one that does decide. A new kind of
 * credential is a new Authenticator; this class does not change (Open/Closed).
 *
 * <p>Failures are reported with {@link AuthenticationFailedException} (401). Its message is a
 * fixed text: the credential itself is never put into a message or a log.
 */
public final class AuthenticationFilter implements GatewayFilter {

    /** Runs first: nothing else should happen for a caller we cannot identify. */
    public static final int ORDER = 100;

    private final List<Authenticator> authenticators;

    public AuthenticationFilter(List<Authenticator> authenticators) {
        this.authenticators = List.copyOf(Objects.requireNonNull(authenticators, "authenticators"));
        if (this.authenticators.isEmpty()) {
            throw new IllegalArgumentException("at least one authenticator is required");
        }
    }

    @Override
    public FilterResult filter(RequestContext context, FilterChain chain) {
        IncomingRequest request = context.request();
        for (Authenticator authenticator : authenticators) {
            if (authenticator.supports(request)) {
                Principal principal = authenticator.authenticate(request).orElseThrow(() ->
                        new AuthenticationFailedException(
                                "the credential is invalid, expired or revoked"));
                return chain.proceed(context.withPrincipal(principal));
            }
        }
        throw new AuthenticationFailedException(
                "no credential was sent: use the X-API-Key header or Authorization: Bearer");
    }

    @Override
    public int order() {
        return ORDER;
    }
}
