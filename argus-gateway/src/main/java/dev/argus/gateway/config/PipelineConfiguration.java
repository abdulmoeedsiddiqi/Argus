package dev.argus.gateway.config;

import dev.argus.core.model.UpstreamInstance;
import dev.argus.core.pipeline.FilterChain;
import dev.argus.core.pipeline.FilterChainFactory;
import dev.argus.core.pipeline.GatewayFilter;
import dev.argus.core.resilience.ProxyFilter;
import dev.argus.core.routing.UpstreamClient;
import dev.argus.gateway.upstream.JdkHttpUpstreamClient;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition root of the request pipeline: the one place where core classes are connected to
 * their adapters. The core itself never sees Spring; it only receives its collaborators through
 * constructors (Dependency Inversion).
 */
@Configuration
@EnableConfigurationProperties(UpstreamProperties.class)
public class PipelineConfiguration {

    @Bean
    UpstreamClient upstreamClient() {
        return new JdkHttpUpstreamClient();
    }

    @Bean
    GatewayFilter proxyFilter(UpstreamClient client, UpstreamProperties upstream) {
        UpstreamInstance target =
                new UpstreamInstance("upstream-1", "default", upstream.url(), 1, null);
        return new ProxyFilter(client, target, upstream.timeout());
    }

    /** Every GatewayFilter bean joins the chain; the factory orders them by order(). */
    @Bean
    FilterChain filterChain(List<GatewayFilter> filters) {
        return new FilterChainFactory().create(filters);
    }
}
