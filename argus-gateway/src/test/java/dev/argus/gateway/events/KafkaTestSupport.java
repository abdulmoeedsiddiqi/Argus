package dev.argus.gateway.events;

import dev.argus.contracts.ConfigChange;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One real Kafka broker (KRaft mode, the same image as in Docker Compose) shared by the
 * gateway's integration tests, started once per test JVM by Testcontainers. The helper
 * methods play the role of the Admin API: they create the compacted topic and publish changes.
 */
public final class KafkaTestSupport {

    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));

    static {
        KAFKA.start();
    }

    private KafkaTestSupport() { }

    public static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    /** Creates a compacted topic with one partition, like gateway.config. */
    public static void createCompactedTopic(String topic) throws Exception {
        try (AdminClient admin = AdminClient.create(
                Map.<String, Object>of("bootstrap.servers", bootstrapServers()))) {
            NewTopic newTopic = new NewTopic(topic, 1, (short) 1)
                    .configs(Map.of("cleanup.policy", "compact"));
            admin.createTopics(List.of(newTopic)).all().get(30, TimeUnit.SECONDS);
        }
    }

    /** Publishes changes the way the Admin API does: keyed by entity, null value for a deletion. */
    public static void publish(String topic, ConfigChange... changes) throws Exception {
        Map<String, Object> properties = Map.of(
                "bootstrap.servers", bootstrapServers(),
                "key.serializer", StringSerializer.class,
                "value.serializer", StringSerializer.class,
                "acks", "all");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(properties)) {
            for (ConfigChange change : changes) {
                String value = change.isDeletion() ? null : ConfigMessages.toJson(change);
                producer.send(new ProducerRecord<>(topic, change.key(), value))
                        .get(30, TimeUnit.SECONDS);
            }
        }
    }
}
