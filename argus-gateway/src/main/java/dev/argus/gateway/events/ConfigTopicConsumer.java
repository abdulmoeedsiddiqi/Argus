package dev.argus.gateway.events;

import dev.argus.contracts.ConfigChange;
import dev.argus.core.events.ConfigurationListener;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observer subject (SE423 report, Section 7.8): reads the compacted configuration topic and
 * notifies every registered {@link ConfigurationListener}.
 *
 * <p>How it works, on one background thread:
 * <ol>
 *   <li>Start at the very beginning of the topic (no consumer group, no stored position), so
 *       every instance, new or restarted, reads the complete configuration.</li>
 *   <li>Note where the topic ends right now. Everything up to there is the "replay".</li>
 *   <li>Hand each record to the listeners. When the replay is done, open the
 *       {@link ReadinessGate}: from now on the instance may serve requests (QR-09).</li>
 *   <li>Keep reading for ever: later changes arrive within moments (QR-12).</li>
 * </ol>
 */
public final class ConfigTopicConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigTopicConsumer.class);
    private static final Duration POLL = Duration.ofMillis(250);
    private static final Duration METADATA_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration RETRY_PAUSE = Duration.ofSeconds(1);

    private final Map<String, Object> properties;
    private final String topic;
    private final List<ConfigurationListener> listeners;
    private final ConfigChangeReader reader;
    private final ReadinessGate gate;

    private volatile boolean running;
    private volatile Consumer<String, String> consumer;
    private Thread thread;

    public ConfigTopicConsumer(
            Map<String, Object> connection,
            String topic,
            List<ConfigurationListener> listeners,
            ConfigChangeReader reader,
            ReadinessGate gate) {
        this.properties = new HashMap<>(connection);
        this.properties.put("key.deserializer", StringDeserializer.class);
        this.properties.put("value.deserializer", StringDeserializer.class);
        this.properties.put("enable.auto.commit", false);
        // The Admin API creates the topic as a compacted one; reading must never create it.
        this.properties.put("allow.auto.create.topics", false);
        this.topic = topic;
        this.listeners = List.copyOf(listeners);
        this.reader = reader;
        this.gate = gate;
    }

    public void start() {
        running = true;
        thread = new Thread(this::run, "config-topic-consumer");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        Consumer<String, String> current = consumer;
        if (current != null) {
            current.wakeup();
        }
        if (thread != null) {
            try {
                thread.join(Duration.ofSeconds(5).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void run() {
        try (Consumer<String, String> kafka = new KafkaConsumer<>(properties)) {
            consumer = kafka;
            List<TopicPartition> partitions = waitForTopic(kafka);
            kafka.assign(partitions);
            kafka.seekToBeginning(partitions);
            Map<TopicPartition, Long> replayEnd = kafka.endOffsets(partitions);
            while (running) {
                for (ConsumerRecord<String, String> record : kafka.poll(POLL)) {
                    deliver(record);
                }
                if (!gate.isOpen() && reached(kafka, replayEnd)) {
                    gate.open();
                    LOG.info("configuration topic {} replayed; this instance is ready", topic);
                }
            }
        } catch (WakeupException stopping) {
            // stop() woke the consumer up: the normal way out of the loop.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException failure) {
            LOG.error("configuration topic consumer stopped unexpectedly", failure);
        }
    }

    /** Waits until the topic exists (the Admin API creates it) and returns its partitions. */
    private List<TopicPartition> waitForTopic(Consumer<String, String> kafka)
            throws InterruptedException {
        boolean reported = false;
        while (true) {
            List<PartitionInfo> found = lookUpTopic(kafka);
            if (found != null && !found.isEmpty()) {
                return found.stream()
                        .map(info -> new TopicPartition(info.topic(), info.partition()))
                        .toList();
            }
            if (!reported) {
                LOG.warn("configuration topic {} is not available yet; not ready", topic);
                reported = true;
            }
            if (!running) {
                throw new WakeupException();
            }
            Thread.sleep(RETRY_PAUSE.toMillis());
        }
    }

    private List<PartitionInfo> lookUpTopic(Consumer<String, String> kafka) {
        try {
            return kafka.partitionsFor(topic, METADATA_TIMEOUT);
        } catch (WakeupException stopping) {
            throw stopping;
        } catch (RuntimeException unavailable) {
            // Kafka cannot be reached (or does not know the topic): try again.
            return null;
        }
    }

    private void deliver(ConsumerRecord<String, String> record) {
        ConfigChange change;
        try {
            change = reader.fromRecord(record.key(), record.value());
        } catch (RuntimeException unreadable) {
            LOG.warn("skipped configuration record at offset {}: {}",
                    record.offset(), unreadable.getMessage());
            return;
        }
        for (ConfigurationListener listener : listeners) {
            try {
                listener.onChange(change);
            } catch (RuntimeException failure) {
                // One failing listener must not keep the change from the others.
                LOG.warn("a listener failed on configuration change {}: {}",
                        change.key(), failure.getMessage());
            }
        }
    }

    /** True once the read position has passed everything that existed when we started. */
    private static boolean reached(
            Consumer<String, String> kafka, Map<TopicPartition, Long> replayEnd) {
        for (Map.Entry<TopicPartition, Long> end : replayEnd.entrySet()) {
            if (kafka.position(end.getKey()) < end.getValue()) {
                return false;
            }
        }
        return true;
    }
}
