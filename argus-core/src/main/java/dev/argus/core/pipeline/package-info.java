/**
 * Request pipeline: the Chain of Responsibility that every request passes through.
 * Implementations (AuthenticationFilter, EnforcementFilter, RateLimitFilter,
 * RoutingFilter, ProxyFilter, DefaultFilterChain, FilterChainFactory) are added
 * test-first in Milestone 2.
 */
package dev.argus.core.pipeline;
