package dev.argus.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.argus.contracts.ConfigChange.EntityType;
import dev.argus.core.auth.ApiKeyHasher;
import dev.argus.gateway.events.ConfigMessages;
import dev.argus.gateway.events.KafkaTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Module M5 on the gateway side (UC-04, UC-05): one real gateway instance, a real Kafka broker
 * (Testcontainers) and a real backend. The test publishes to the configuration topic exactly
 * what the Admin API publishes, and watches the running gateway change its behaviour.
 * The steps build on each other, so they run in order.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConfigPropagationIntegrationTest {

    private static final String TOPIC = "config-" + UUID.randomUUID();
    private static final String KEY = "ak_test_propagation_0123456789";
    private static final String KEY_HASH = new ApiKeyHasher().hash(KEY);
    private static final MockBackend ORDERS = MockBackend.start("orders-a");

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int gatewayPort;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws Exception {
        KafkaTestSupport.createCompactedTopic(TOPIC);
        // Written before the gateway starts: it must be there after the start-up replay.
        KafkaTestSupport.publish(TOPIC, ConfigMessages.apiKey("key-1", KEY_HASH, false));
        registry.add("argus.config.source", () -> "kafka");
        registry.add("argus.config.topic", () -> TOPIC);
        registry.add("argus.config.bootstrap-servers", KafkaTestSupport::bootstrapServers);
        registry.add("argus.health-check.enabled", () -> "false");
    }

    @AfterAll
    static void stopBackend() {
        ORDERS.close();
    }

    @Test
    @Order(1)
    @DisplayName("TC-12.2: a key published before start-up is known after the replay (404: no route yet)")
    void replayedKeyIsKnown() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(
                () -> assertThat(status("/orders/42")).isEqualTo(404));
    }

    @Test
    @Order(2)
    @DisplayName("TC-12.1: a route and a backend copy published to the topic are used by the gateway")
    void publishedRouteIsUsed() throws Exception {
        KafkaTestSupport.publish(TOPIC,
                ConfigMessages.upstream("orders-a", "orders", ORDERS.url()),
                ConfigMessages.route("orders", "/orders", "orders"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> assertThat(status("/orders/42")).isEqualTo(200));
    }

    @Test
    @Order(3)
    @DisplayName("QR-12: a key revoked through the topic is refused with 401 within 5 s")
    void revokedKeyIsRefusedWithinFiveSeconds() throws Exception {
        assertThat(status("/orders/42")).isEqualTo(200);

        KafkaTestSupport.publish(TOPIC, ConfigMessages.apiKey("key-1", KEY_HASH, true));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(
                () -> assertThat(status("/orders/42")).isEqualTo(401));
    }

    @Test
    @Order(4)
    @DisplayName("FR-12: a deleted route (tombstone) stops matching")
    void deletedRouteStopsMatching() throws Exception {
        KafkaTestSupport.publish(TOPIC,
                ConfigMessages.apiKey("key-2", new ApiKeyHasher().hash(KEY + "-2"), false));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> assertThat(status("/orders/42", KEY + "-2")).isEqualTo(200));

        KafkaTestSupport.publish(TOPIC, ConfigMessages.deleted(EntityType.ROUTE, "orders"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> assertThat(status("/orders/42", KEY + "-2")).isEqualTo(404));
    }

    private int status(String path) throws Exception {
        return status(path, KEY);
    }

    private int status(String path, String key) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + gatewayPort + path))
                .header("X-API-Key", key)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
