package dev.argus.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Walking skeleton (scope document, Section 7.5): one request travels
 * client -> gateway -> mock-service -> client.
 *
 * <p>Both applications are the real ones, each on its own random port, talking over real HTTP.
 * The gateway is configured with the smallest possible setup: one route to one backend copy.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class WalkingSkeletonIntegrationTest {

    /** The real mock backend, started once before the gateway context is built. */
    private static final MockBackend ORDERS = MockBackend.start("orders");

    @LocalServerPort
    private int gatewayPort;

    @DynamicPropertySource
    static void oneRouteToOneBackend(DynamicPropertyRegistry registry) {
        registry.add("argus.upstreams[0].id", () -> "orders-1");
        registry.add("argus.upstreams[0].service", () -> "orders");
        registry.add("argus.upstreams[0].url", ORDERS::url);
        registry.add("argus.routes[0].path-prefix", () -> "/orders");
        registry.add("argus.routes[0].service", () -> "orders");
        registry.add("argus.health-check.enabled", () -> "false");
    }

    @AfterAll
    static void stopBackend() {
        ORDERS.close();
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
