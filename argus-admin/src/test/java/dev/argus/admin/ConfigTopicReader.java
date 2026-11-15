package dev.argus.admin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.argus.contracts.ConfigChange;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Test helper: reads the configuration topic from the beginning, like a gateway instance, and
 * returns what was published for one entity.
 */
public final class ConfigTopicReader {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final String bootstrapServers;
    private final String topic;

    ConfigTopicReader(String bootstrapServers, String topic) {
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
    }

    /** Everything published so far under one key, oldest first. */
    public List<Published> recordsFor(String key) {
        Map<String, Object> properties = Map.of(
                "bootstrap.servers", bootstrapServers,
                "key.deserializer", StringDeserializer.class,
                "value.deserializer", StringDeserializer.class,
                "enable.auto.commit", false);
        List<Published> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            TopicPartition partition = new TopicPartition(topic, 0);
            consumer.assign(List.of(partition));
            consumer.seekToBeginning(List.of(partition));
            long end = consumer.endOffsets(List.of(partition)).get(partition);
            while (consumer.position(partition) < end) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    if (record.key().equals(key)) {
                        found.add(new Published(record.key(), record.value()));
                    }
                }
            }
        }
        return found;
    }

    /** The latest record of one key; fails if nothing was published for it. */
    public Published latest(String key) {
        List<Published> records = recordsFor(key);
        if (records.isEmpty()) {
            throw new AssertionError("nothing was published for " + key);
        }
        return records.get(records.size() - 1);
    }

    /** The cleanup policy of the topic, for example "compact". */
    public String cleanupPolicy() throws Exception {
        try (AdminClient admin = admin()) {
            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
            Config config = admin.describeConfigs(List.of(resource)).all().get().get(resource);
            return config.get("cleanup.policy").value();
        }
    }

    public int partitions() throws Exception {
        try (AdminClient admin = admin()) {
            TopicDescription description =
                    admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic);
            return description.partitions().size();
        }
    }

    private AdminClient admin() {
        return AdminClient.create(Map.<String, Object>of("bootstrap.servers", bootstrapServers));
    }

    /** One record of the topic. A null value is a tombstone: the entity was deleted. */
    public record Published(String key, String value) {

        public boolean isTombstone() {
            return value == null;
        }

        public ConfigChange change() throws Exception {
            return JSON.readValue(value, ConfigChange.class);
        }

        /** The payload (RouteConfig, ApiKeyConfig, ...) as a JSON tree. */
        public JsonNode payload() throws Exception {
            return JSON.readTree(change().payloadJson());
        }
    }
}
