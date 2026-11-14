package dev.argus.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.argus.mock.MockServiceApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Walking skeleton (scope document, Section 7.5): one request travels
 * client -> gateway -> mock-service -> client before any feature is added.
 *
 * <p>Both applications are the real ones, each on its own random port, talking over real HTTP.
 * This is the first step towards UC-01; route matching (TC-01.1) and authentication are not
 * part of it yet, so the test claims neither.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WalkingSkeletonIntegrationTest {

    /** The real mock backend, started once before the gateway's context is built. */
    private static final ConfigurableApplicationContext MOCK_SERVICE =
            new SpringApplicationBuilder(MockServiceApplication.class)
                    .properties(
                            "server.port=0",
                            "mock.service-name=orders",
                            "spring.application.name=mock-service",
                            "spring.jmx.enabled=false")
                    .run();

    @LocalServerPort
    private int gatewayPort;

    @DynamicPropertySource
    static void pointGatewayAtMockService(DynamicPropertyRegistry registry) {
        int mockPort = ((ServletWebServerApplicationContext) MOCK_SERVICE).getWebServer().getPort();
        registry.add("argus.upstream.url", () -> "http://127.0.0.1:" + mockPort);
    }

    @AfterAll
    static void stopMockService() {
        MOCK_SERVICE.close();
    }

    @Test
    @DisplayName("UC-01 walking skeleton: client -> gateway -> mock-service -> client")
    void requestTravelsThroughGatewayToMockServiceAndBack() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + gatewayPort + "/orders/42"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode order = new ObjectMapper().readTree(response.body());
        assertThat(order.get("id").asInt()).isEqualTo(42);
        assertThat(order.get("item").asText()).isEqualTo("Pizza");
        assertThat(order.get("status").asText()).isEqualTo("DELIVERED");
        // Set only by the mock backend: proves the answer came from it, through the gateway.
        assertThat(response.headers().firstValue("X-Mock-Service")).contains("orders");
    }
}
