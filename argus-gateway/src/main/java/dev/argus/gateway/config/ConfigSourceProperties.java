package dev.argus.gateway.config;

import dev.argus.contracts.Topics;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where routes, backend copies and API keys come from ({@code argus.config}).
 *
 * @param source            STATIC: from the configuration file, complete at start-up.
 *                          KAFKA: from the configuration topic written by the Admin API.
 * @param topic             name of the compacted configuration topic
 * @param bootstrapServers  address of the Kafka brokers
 * @param instanceId        name of this gateway instance, shown in Kafka as the client id
 */
@ConfigurationProperties("argus.config")
public record ConfigSourceProperties(
        @DefaultValue("STATIC") Source source,
        @DefaultValue(Topics.CONFIG) String topic,
        @DefaultValue("localhost:9092") String bootstrapServers,
        @DefaultValue("gateway") String instanceId) {

    /** The two ways a gateway can get its configuration. */
    public enum Source { STATIC, KAFKA }
}
