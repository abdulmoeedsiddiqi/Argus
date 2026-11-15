package dev.argus.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Module M1 end to end: the real gateway in front of two real copies of the Orders backend.
 * Covers route matching (FR-01) and round-robin load balancing (FR-02) over real HTTP.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RoutingIntegrationTest {

    private static final MockBackend ORDERS_A = MockBackend.start("orders-a");
    private static final MockBackend ORDERS_B = MockBackend.start("orders-b");

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @LocalServerPort
    private int gatewayPort;

    @DynamicPropertySource
    static void ordersRouteWithTwoCopies(DynamicPropertyRegistry registry) {
        registry.add("argus.upstreams[0].id", () -> "orders-a");
        registry.add("argus.upstreams[0].service", () -> "orders");
        registry.add("argus.upstreams[0].url", ORDERS_A::url);
        registry.add("argus.upstreams[1].id", () -> "orders-b");
        registry.add("argus.upstreams[1].service", () -> "orders");
        registry.add("argus.upstreams[1].url", ORDERS_B::url);
        registry.add("argus.routes[0].path-prefix", () -> "/orders");
        registry.add("argus.routes[0].service", () -> "orders");
        registry.add("argus.routes[0].methods", () -> "GET");
        registry.add("argus.routes[0].load-balancing", () -> "ROUND_ROBIN");
        registry.add("argus.health-check.enabled", () -> "false");
        TestCredentials.register(registry);
    }

    @AfterAll
    static void stopBackends() {
        ORDERS_A.close();
        ORDERS_B.close();
    }

    @Test
    @DisplayName("TC-01.1: a request on a configured route is forwarded and answered by the backend")
    void matchedRouteIsForwarded() throws Exception {
        HttpResponse<String> response = send("GET", "/orders/42");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("id").asInt()).isEqualTo(42);
    }

    @Test
    @DisplayName("TC-01.2: an unknown path returns 404 with reason ROUTE_NOT_FOUND")
    void unknownPathReturns404() throws Exception {
        HttpResponse<String> response = send("GET", "/payments/1");

        assertThat(response.statusCode()).isEqualTo(404);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("reason").asText()).isEqualTo("ROUTE_NOT_FOUND");
    }

    @Test
    @DisplayName("TC-01.3: a method the route does not accept returns 404 with reason ROUTE_NOT_FOUND")
    void wrongMethodReturns404() throws Exception {
        HttpResponse<String> response = send("POST", "/orders");

        assertThat(response.statusCode()).isEqualTo(404);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("reason").asText()).isEqualTo("ROUTE_NOT_FOUND");
    }

    @Test
    @DisplayName("TC-02.1: round robin spreads 10 requests evenly over the two backend copies")
    void roundRobinSpreadsRequestsEvenly() throws Exception {
        Map<String, Integer> answersPerCopy = new HashMap<>();

        for (int i = 0; i < 10; i++) {
            HttpResponse<String> response = send("GET", "/orders/" + i);
            String copy = response.headers().firstValue("X-Mock-Service").orElseThrow();
            answersPerCopy.merge(copy, 1, Integer::sum);
        }

        assertThat(answersPerCopy).containsOnly(Map.entry("orders-a", 5), Map.entry("orders-b", 5));
    }

    private HttpResponse<String> send(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + gatewayPort + path))
                .header(TestCredentials.API_KEY_HEADER, TestCredentials.VALID_KEY)
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
