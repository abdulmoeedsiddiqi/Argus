package dev.argus.gateway.upstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.argus.core.exception.NoHealthyUpstreamException;
import dev.argus.core.exception.UpstreamTimeoutException;
import dev.argus.core.model.HttpMethod;
import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.UpstreamInstance;
import dev.argus.core.model.UpstreamResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Adapter test (SE423 report, Section 7.7): JdkHttpUpstreamClient fits java.net.http to the
 * core's UpstreamClient port. The stub backend is the JDK's built-in HttpServer.
 */
class JdkHttpUpstreamClientTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final JdkHttpUpstreamClient client = new JdkHttpUpstreamClient();
    private HttpServer stub;
    private UpstreamInstance stubInstance;

    @BeforeEach
    void startStub() throws IOException {
        stub = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        stub.start();
        stubInstance = instanceAt(stub.getAddress().getPort());
    }

    @AfterEach
    void stopStub() {
        stub.stop(0);
    }

    @Test
    @DisplayName("sends method, path with query, headers and body to the upstream instance")
    void sendsTheRequestUnchanged() {
        AtomicReference<String> seenMethod = new AtomicReference<>();
        AtomicReference<String> seenUri = new AtomicReference<>();
        AtomicReference<String> seenHeader = new AtomicReference<>();
        AtomicReference<String> seenBody = new AtomicReference<>();
        stub.createContext("/", exchange -> {
            seenMethod.set(exchange.getRequestMethod());
            seenUri.set(exchange.getRequestURI().toString());
            seenHeader.set(exchange.getRequestHeaders().getFirst("X-Trace"));
            seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 201, "created");
        });
        IncomingRequest request = request(HttpMethod.POST, "/orders?expand=items",
                Map.of("X-Trace", List.of("abc"),
                        // Headers the JDK client sets itself; the adapter must not copy them.
                        "Host", List.of("gateway.example"),
                        "Content-Length", List.of("12"),
                        "Connection", List.of("keep-alive")),
                "{\"item\":\"Pizza\"}");

        client.send(request, stubInstance, TIMEOUT);

        assertThat(seenMethod.get()).isEqualTo("POST");
        assertThat(seenUri.get()).isEqualTo("/orders?expand=items");
        assertThat(seenHeader.get()).isEqualTo("abc");
        assertThat(seenBody.get()).isEqualTo("{\"item\":\"Pizza\"}");
    }

    @Test
    @DisplayName("returns the upstream's status, headers and body, and names the instance")
    void returnsTheUpstreamResponse() {
        stub.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("X-Mock-Service", "orders");
            respond(exchange, 200, "{\"id\":42}");
        });

        UpstreamResponse response = client.send(
                request(HttpMethod.GET, "/orders/42", Map.of(), null), stubInstance, TIMEOUT);

        assertThat(response.status()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":42}");
        assertThat(response.headers()).containsEntry("x-mock-service", List.of("orders"));
        assertThat(response.upstreamInstanceId()).isEqualTo("stub-1");
        assertThat(response.latency()).isPositive();
    }

    @Test
    @DisplayName("an upstream error status is a response, not an exception")
    void upstreamErrorStatusIsReturned() {
        stub.createContext("/", exchange -> respond(exchange, 500, "boom"));

        UpstreamResponse response = client.send(
                request(HttpMethod.GET, "/orders/42", Map.of(), null), stubInstance, TIMEOUT);

        assertThat(response.status()).isEqualTo(500);
    }

    @Test
    @DisplayName("an upstream slower than the timeout raises UpstreamTimeoutException (504)")
    void slowUpstreamTimesOut() {
        stub.createContext("/", exchange -> {
            pause(Duration.ofMillis(1500));
            respond(exchange, 200, "too late");
        });
        IncomingRequest request = request(HttpMethod.GET, "/orders/42", Map.of(), null);

        assertThatThrownBy(() -> client.send(request, stubInstance, Duration.ofMillis(200)))
                .isInstanceOf(UpstreamTimeoutException.class)
                .hasMessageContaining("stub-1");
    }

    @Test
    @DisplayName("an unreachable upstream raises NoHealthyUpstreamException (503)")
    void unreachableUpstreamIsReported() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        IncomingRequest request = request(HttpMethod.GET, "/orders/42", Map.of(), null);

        assertThatThrownBy(() -> client.send(request, instanceAt(closedPort), TIMEOUT))
                .isInstanceOf(NoHealthyUpstreamException.class);
    }

    private static UpstreamInstance instanceAt(int port) {
        return new UpstreamInstance(
                "stub-1", "orders", URI.create("http://127.0.0.1:" + port), 1, "v1");
    }

    private static IncomingRequest request(
            HttpMethod method, String path, Map<String, List<String>> headers, String body) {
        byte[] bytes = body == null ? null : body.getBytes(StandardCharsets.UTF_8);
        return new IncomingRequest("req-1", method, path, headers, bytes, "127.0.0.1", Instant.now());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
