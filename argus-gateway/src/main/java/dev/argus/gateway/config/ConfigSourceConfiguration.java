package dev.argus.gateway.config;

import dev.argus.core.events.ConfigurationListener;
import dev.argus.core.routing.InMemoryUpstreamRegistry;
import dev.argus.core.routing.RouteRegistry;
import dev.argus.gateway.events.ConfigChangeReader;
import dev.argus.gateway.events.ConfigTopicConsumer;
import dev.argus.gateway.events.ReadinessGate;
import dev.argus.gateway.events.RouteConfigApplier;
import java.util.List;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Where the gateway's routes and keys come from, and when it may start serving (module M5).
 *
 * <p>With {@code argus.config.source=static} everything comes from the configuration file and
 * the gate is open at once. With {@code kafka} the {@link ConfigTopicConsumer} replays the
 * configuration topic first; the gate opens when the replay is complete.
 */
@Configuration
@EnableConfigurationProperties(ConfigSourceProperties.class)
public class ConfigSourceConfiguration {

    @Bean
    ReadinessGate readinessGate(ConfigSourceProperties properties) {
        return properties.source() == ConfigSourceProperties.Source.KAFKA
                ? ReadinessGate.closed()
                : ReadinessGate.alreadyOpen();
    }

    @Bean
    RouteConfigApplier routeConfigApplier(RouteRegistry routes, InMemoryUpstreamRegistry upstreams) {
        return new RouteConfigApplier(routes, upstreams, new ConfigChangeReader());
    }

    /** The listeners are found by type: every ConfigurationListener bean is registered. */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(name = "argus.config.source", havingValue = "kafka")
    ConfigTopicConsumer configTopicConsumer(
            ConfigSourceProperties properties,
            List<ConfigurationListener> listeners,
            ReadinessGate gate) {
        Map<String, Object> connection = Map.of(
                "bootstrap.servers", properties.bootstrapServers(),
                "client.id", properties.instanceId() + "-config");
        return new ConfigTopicConsumer(
                connection, properties.topic(), listeners, new ConfigChangeReader(), gate);
    }

    /**
     * Part of the readiness health group ({@code /actuator/health/readiness}): DOWN until the
     * configuration has been replayed, so Docker and Kubernetes also see "not ready".
     */
    @Bean
    HealthIndicator configReplayHealthIndicator(ReadinessGate gate) {
        return () -> gate.isOpen()
                ? Health.up().build()
                : Health.down().withDetail("reason", "configuration topic not replayed yet").build();
    }
}
