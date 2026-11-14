/**
 * Request pipeline: the Chain of Responsibility that every request passes through
 * ({@code GatewayFilter}, {@code FilterChain}, {@code DefaultFilterChain},
 * {@code FilterChainFactory}).
 *
 * <p>The filters themselves live in the package of the module they belong to
 * ({@code RoutingFilter} in routing, {@code ProxyFilter} in resilience, and so on). This package
 * therefore depends only on {@code model}, which keeps the package graph free of cycles.
 */
package dev.argus.core.pipeline;
