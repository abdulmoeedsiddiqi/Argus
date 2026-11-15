package dev.argus.gateway.config;

import dev.argus.core.auth.ApiKeyAuthenticator;
import dev.argus.core.auth.ApiKeyHasher;
import dev.argus.core.auth.ApiKeyStore;
import dev.argus.core.auth.AuthenticationFilter;
import dev.argus.core.auth.Authenticator;
import dev.argus.core.loadbalancing.DefaultLoadBalancingStrategyFactory;
import dev.argus.core.loadbalancing.LoadBalancingStrategyFactory;
import dev.argus.core.pipeline.FilterChain;
import dev.argus.core.pipeline.FilterChainFactory;
import dev.argus.core.pipeline.GatewayFilter;
import dev.argus.core.resilience.ProxyFilter;
import dev.argus.core.routing.CanarySelector;
import dev.argus.core.routing.HealthChecker;
import dev.argus.core.routing.HealthProbe;
import dev.argus.core.routing.InMemoryUpstreamRegistry;
import dev.argus.core.routing.RouteRegistry;
import dev.argus.core.routing.RoutingFilter;
import dev.argus.core.routing.SnapshotRouteRegistry;
import dev.argus.core.routing.UpstreamClient;
import dev.argus.core.routing.UpstreamRegistry;
import dev.argus.core.time.TimeSource;
import dev.argus.gateway.auth.ApiKeyCache;
import dev.argus.gateway.auth.JwtAuthenticator;
import dev.argus.gateway.upstream.JdkHttpHealthProbe;
import dev.argus.gateway.upstream.JdkHttpUpstreamClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root of the request pipeline: the one place where core classes are connected to
 * each other and to their adapters. The core itself never sees Spring; it only receives its
 * collaborators through constructors (Dependency Inversion).
 */
@Configuration
@EnableConfigurationProperties({StaticRoutingProperties.class, AuthProperties.class})
public class PipelineConfiguration {

    @Bean
    UpstreamClient upstreamClient() {
        return new JdkHttpUpstreamClient();
    }

    /** Route table, filled from configuration at start-up (until module M5 feeds it). */
    @Bean
    SnapshotRouteRegistry routeRegistry(StaticRoutingProperties properties) {
        SnapshotRouteRegistry registry = new SnapshotRouteRegistry();
        registry.replaceAll(properties.toRoutes());
        return registry;
    }

    /** Backend copies, filled from configuration at start-up. */
    @Bean
    InMemoryUpstreamRegistry upstreamRegistry(StaticRoutingProperties properties) {
        InMemoryUpstreamRegistry registry = new InMemoryUpstreamRegistry();
        registry.replaceAll(properties.toInstances());
        return registry;
    }

    /** The real clock. Tests of time-based logic pass a fixed clock instead. */
    @Bean
    TimeSource timeSource() {
        return TimeSource.system();
    }

    /** API key records (hashes only), filled from configuration until module M5 feeds them. */
    @Bean
    ApiKeyCache apiKeyCache(AuthProperties properties) {
        ApiKeyCache cache = new ApiKeyCache();
        cache.replaceAll(properties.toRecords());
        return cache;
    }

    /**
     * Authentication step (module M2). API keys are always accepted; JWTs only when a secret
     * is configured. The filter sees both through the same Authenticator interface.
     */
    @Bean
    GatewayFilter authenticationFilter(
            ApiKeyStore apiKeys, AuthProperties properties, TimeSource time) {
        List<Authenticator> authenticators = new ArrayList<>();
        authenticators.add(new ApiKeyAuthenticator(apiKeys, new ApiKeyHasher(), time));
        if (properties.jwtEnabled()) {
            AuthProperties.Jwt jwt = properties.jwt();
            authenticators.add(new JwtAuthenticator(
                    jwt.secret().getBytes(StandardCharsets.UTF_8), jwt.issuer(), time));
        }
        return new AuthenticationFilter(authenticators);
    }

    @Bean
    HealthProbe healthProbe(StaticRoutingProperties properties) {
        StaticRoutingProperties.HealthCheck check = properties.healthCheck();
        return new JdkHttpHealthProbe(check.path(), check.timeout());
    }

    /**
     * Active health checks (FR-03). When enabled, rounds run periodically in the background;
     * Spring calls stop() on shutdown. Tests disable the schedule and call checkAll() directly.
     */
    @Bean(destroyMethod = "stop")
    HealthChecker healthChecker(
            InMemoryUpstreamRegistry upstreams, HealthProbe probe, StaticRoutingProperties properties) {
        StaticRoutingProperties.HealthCheck check = properties.healthCheck();
        HealthChecker checker = new HealthChecker(
                upstreams::allInstances, probe, upstreams, check.failureThreshold());
        if (check.enabled()) {
            checker.start(check.interval());
        }
        return checker;
    }

    @Bean
    LoadBalancingStrategyFactory loadBalancingStrategyFactory() {
        return new DefaultLoadBalancingStrategyFactory();
    }

    @Bean
    GatewayFilter routingFilter(
            RouteRegistry routes, UpstreamRegistry upstreams, LoadBalancingStrategyFactory factory) {
        return new RoutingFilter(routes, upstreams, factory, new CanarySelector());
    }

    @Bean
    GatewayFilter proxyFilter(UpstreamClient client) {
        return new ProxyFilter(client);
    }

    /** Every GatewayFilter bean joins the chain; the factory orders them by order(). */
    @Bean
    FilterChain filterChain(List<GatewayFilter> filters) {
        return new FilterChainFactory().create(filters);
    }
}
