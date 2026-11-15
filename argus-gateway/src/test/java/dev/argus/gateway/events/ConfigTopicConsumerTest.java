package dev.argus.gateway.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.argus.contracts.ConfigChange;
import dev.argus.contracts.ConfigChange.EntityType;
import dev.argus.core.events.ConfigurationListener;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-12 / QR-09 against a real Kafka broker (Testcontainers): the consumer replays the whole
 * configuration topic, notifies every listener (Observer), and only then opens the gate.
 */
class ConfigTopicConsumerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String topic = "config-" + UUID.randomUUID();
    private final ReadinessGate gate = ReadinessGate.closed();
    private ConfigTopicConsumer consumer;

    @AfterEach
    void stopConsumer() {
        if (consumer != null) {
            consumer.stop();
        }
    }

    @Test
    @DisplayName("TC-12.2: changes written before start-up are replayed, in order, to every listener")
    void replaysTheTopicToEveryListener() throws Exception {
        KafkaTestSupport.createCompactedTopic(topic);
        KafkaTestSupport.publish(topic,
                ConfigMessages.route("orders", "/orders", "orders"),
                ConfigMessages.apiKey("key-1", "hash-1", false),
                ConfigMessages.deleted(EntityType.ROUTE, "orders"));
        Recorder first = new Recorder();
        Recorder second = new Recorder();

        start(first, second);

        await().atMost(TIMEOUT).until(gate::isOpen);
        List<String> expected = List.of("ROUTE:orders", "API_KEY:key-1", "ROUTE:orders deleted");
        assertThat(first.seen).isEqualTo(expected);
        assertThat(second.seen).isEqualTo(expected);
    }

    @Test
    @DisplayName("TC-12.3: the gate opens only after the last replayed change was delivered")
    void gateOpensOnlyAfterTheReplay() throws Exception {
        KafkaTestSupport.createCompactedTopic(topic);
        KafkaTestSupport.publish(topic,
                ConfigMessages.apiKey("key-1", "hash-1", false),
                ConfigMessages.apiKey("key-2", "hash-2", false),
                ConfigMessages.apiKey("key-1", "hash-1", true));
        List<Boolean> gateWasOpenDuringDelivery = new CopyOnWriteArrayList<>();

        start(change -> gateWasOpenDuringDelivery.add(gate.isOpen()));

        await().atMost(TIMEOUT).until(gate::isOpen);
        assertThat(gateWasOpenDuringDelivery).containsExactly(false, false, false);
    }

    @Test
    @DisplayName("QR-09: an empty topic has nothing to replay, so the gate opens")
    void emptyTopicOpensTheGate() throws Exception {
        KafkaTestSupport.createCompactedTopic(topic);

        start(new Recorder());

        await().atMost(TIMEOUT).until(gate::isOpen);
    }

    @Test
    @DisplayName("QR-09: while the topic does not exist the gate stays closed; it opens once it does")
    void missingTopicKeepsTheGateClosed() throws Exception {
        start(new Recorder());

        await().pollDelay(Duration.ofSeconds(3)).atMost(TIMEOUT).until(() -> true);
        assertThat(gate.isOpen()).isFalse();

        KafkaTestSupport.createCompactedTopic(topic);
        await().atMost(TIMEOUT).until(gate::isOpen);
    }

    @Test
    @DisplayName("TC-12.1: a change published after the replay reaches the listeners too")
    void liveChangesAreDelivered() throws Exception {
        KafkaTestSupport.createCompactedTopic(topic);
        Recorder recorder = new Recorder();
        start(recorder);
        await().atMost(TIMEOUT).until(gate::isOpen);

        KafkaTestSupport.publish(topic, ConfigMessages.apiKey("key-9", "hash-9", true));

        await().atMost(TIMEOUT).untilAsserted(
                () -> assertThat(recorder.seen).containsExactly("API_KEY:key-9"));
    }

    @Test
    @DisplayName("a listener that throws does not stop the other listeners or the consumer")
    void failingListenerDoesNotStopTheOthers() throws Exception {
        KafkaTestSupport.createCompactedTopic(topic);
        KafkaTestSupport.publish(topic,
                ConfigMessages.apiKey("key-1", "hash-1", false),
                ConfigMessages.apiKey("key-2", "hash-2", false));
        Recorder recorder = new Recorder();

        start(change -> {
            throw new IllegalStateException("listener failure");
        }, recorder);

        await().atMost(TIMEOUT).until(gate::isOpen);
        assertThat(recorder.seen).containsExactly("API_KEY:key-1", "API_KEY:key-2");
    }

    private void start(ConfigurationListener... listeners) {
        Map<String, Object> properties = Map.of(
                "bootstrap.servers", KafkaTestSupport.bootstrapServers());
        consumer = new ConfigTopicConsumer(
                properties, topic, List.of(listeners), new ConfigChangeReader(), gate);
        consumer.start();
    }

    /** A listener that remembers what it was told. */
    private static final class Recorder implements ConfigurationListener {

        private final List<String> seen = new CopyOnWriteArrayList<>();

        @Override
        public void onChange(ConfigChange change) {
            seen.add(change.isDeletion() ? change.key() + " deleted" : change.key());
        }
    }
}
