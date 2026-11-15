package dev.argus.admin.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.argus.admin.config.AdminProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

/** Kafka wiring of the Admin API: the configuration topic and the publisher adapter. */
@Configuration
public class ConfigTopicConfiguration {

    /**
     * Spring creates this topic at start-up if it does not exist. It is compacted (Kafka keeps
     * the latest record of each key for ever) and has one partition, so all changes have one
     * total order and a delete can never overtake the create it belongs to.
     */
    @Bean
    NewTopic configTopic(AdminProperties properties) {
        return TopicBuilder.name(properties.configTopic())
                .partitions(1)
                .replicas(1)
                .compact()
                .build();
    }

    @Bean
    ConfigChangePublisher configChangePublisher(
            KafkaTemplate<String, String> kafka, ObjectMapper json, AdminProperties properties) {
        return new KafkaConfigChangePublisher(kafka, json, properties.configTopic());
    }
}
