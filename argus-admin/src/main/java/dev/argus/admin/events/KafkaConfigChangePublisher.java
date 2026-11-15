package dev.argus.admin.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.argus.contracts.ConfigChange;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Adapter: implements {@link ConfigChangePublisher} with Kafka.
 *
 * <p>Record key: {@code entityType:entityId}. Because the topic is compacted, Kafka keeps the
 * latest record per key, which is exactly "the current state of each entity". A deleted
 * entity is sent with no value at all (a tombstone), which tells Kafka to forget the key.
 *
 * <p>The send waits for the broker's acknowledgement (acks=all), so when {@link #publish}
 * returns, the change is stored in Kafka.
 */
public class KafkaConfigChangePublisher implements ConfigChangePublisher {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final String topic;

    public KafkaConfigChangePublisher(
            KafkaTemplate<String, String> kafka, ObjectMapper json, String topic) {
        this.kafka = kafka;
        this.json = json;
        this.topic = topic;
    }

    @Override
    public void publish(ConfigChange change) {
        try {
            String value = change.isDeletion() ? null : json.writeValueAsString(change);
            kafka.send(topic, change.key(), value).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw notPropagated(change, e);
        } catch (ExecutionException | TimeoutException | JsonProcessingException | RuntimeException e) {
            throw notPropagated(change, e);
        }
    }

    private static ConfigNotPropagatedException notPropagated(ConfigChange change, Exception cause) {
        return new ConfigNotPropagatedException(
                "change " + change.key() + " is saved but could not be sent to the gateways", cause);
    }
}
