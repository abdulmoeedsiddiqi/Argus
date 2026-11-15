package dev.argus.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.argus.gateway.events.KafkaTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * QR-09 readiness gate on a running gateway. The configuration topic does not exist when the
 * gateway starts, so the replay cannot finish: the gateway must refuse traffic and report
 * "not ready". When the topic appears, the replay completes and the gateway opens.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ReadinessGateIntegrationTest {

    private static final String TOPIC = "config-" + UUID.randomUUID();

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int gatewayPort;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("argus.config.source", () -> "kafka");
        registry.add("argus.config.topic", () -> TOPIC);
        registry.add("argus.config.bootstrap-servers", KafkaTestSupport::bootstrapServers);
        registry.add("argus.health-check.enabled", () -> "false");
    }

    @Test
    @DisplayName("TC-12.3: not ready before the replay (503 NOT_READY, readiness DOWN), ready after it")
    void refusesTrafficUntilTheTopicIsReplayed() throws Exception {
        HttpResponse<String> request = get("/orders/42");
        assertThat(request.statusCode()).isEqualTo(503);
        assertThat(request.body()).contains("NOT_READY");
        HttpResponse<String> readiness = get("/actuator/health/readiness");
        assertThat(readiness.statusCode()).isEqualTo(503);
        assertThat(readiness.body()).contains("DOWN");

        KafkaTestSupport.createCompactedTopic(TOPIC);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(
                () -> assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200));
        // Past the gate now: no credential was sent, so the pipeline answers 401.
        assertThat(get("/orders/42").statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + gatewayPort + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
