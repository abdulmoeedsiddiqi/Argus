package dev.argus.core.pipeline;

/** Hands the request to the next filter. The terminal element forwards to the upstream. */
@FunctionalInterface
public interface FilterChain {

    FilterResult proceed(RequestContext context);
}
