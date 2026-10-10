package dev.argus.core.pipeline;

/**
 * One step of the request pipeline (Chain of Responsibility).
 * A filter either rejects the request, or delegates to {@code chain.proceed(...)}.
 * Each implementation owns exactly one concern (authentication, enforcement,
 * rate limiting, routing, proxying).
 */
public interface GatewayFilter {

    FilterResult filter(RequestContext context, FilterChain chain);

    /** Lower values run earlier. Used by the chain factory to order filters. */
    int order();
}
