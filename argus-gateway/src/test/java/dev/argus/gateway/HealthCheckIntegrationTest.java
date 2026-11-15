package dev.argus.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import dev.argus.core.routing.HealthChecker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * FR-03 and QR-01 end to end: a backend copy that fails its health checks stops receiving
 * requests, and receives them again after it recovers.
 *
 * <p>The periodic schedule is switched off and the test calls HealthChecker.checkAll() itself,
 * so each "round" happens exactly when the test says. No sleeping, no flaky timing.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HealthCheckIntegrationTest {

    private static final MockBackend ORDERS_A = MockBackend.start("orders-a");
    private static final MockBackend ORDERS_B = MockBackend.start("orders-b");

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private HealthChecker healthChecker;

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
        registry.add("argus.health-check.enabled", () -> "false");
        registry.add("argus.health-check.failure-threshold", () -> "3");
        registry.add("argus.health-check.timeout", () -> "1s");
        TestCredentials.register(registry);
    }

    @AfterAll
    static void stopBackends() {
        ORDERS_A.close();
        ORDERS_B.close();
    }

    @Test
    @DisplayName("TC-03.1, TC-03.2, TC-Q01.1: a failing copy leaves rotation and rejoins after recovery")
    void failingCopyLeavesRotationAndRejoins() throws Exception {
        healthChecker.checkAll();
        assertThat(copiesAnswering(10)).containsExactlyInAnyOrder("orders-a", "orders-b");

        // TC-03.1: orders-a starts failing; after 3 failed rounds it is out of rotation.
        ORDERS_A.fault("fail");
        healthChecker.checkAll();
        healthChecker.checkAll();
        assertThat(copiesAnswering(10)).contains("orders-a");
        healthChecker.checkAll();

        // TC-Q01.1: no request reaches the copy marked UNHEALTHY, and none fails.
        assertThat(copiesAnswering(20)).containsExactly("orders-b");

        // TC-03.2: orders-a recovers; after one successful round it is back.
        ORDERS_A.fault("ok");
        healthChecker.checkAll();
        assertThat(copiesAnswering(10)).containsExactlyInAnyOrder("orders-a", "orders-b");
    }

    /** Sends {@code requests} requests and returns the backend copies named in the answers. */
    private Set<String> copiesAnswering(int requests) throws Exception {
        Set<String> copies = new HashSet<>();
        for (int i = 0; i < requests; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + gatewayPort + "/orders/" + i))
                    .header(TestCredentials.API_KEY_HEADER, TestCredentials.VALID_KEY)
                    .GET()
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            copies.add(response.headers().firstValue("X-Argus-Upstream").orElse("none"));
        }
        return copies;
    }
}
