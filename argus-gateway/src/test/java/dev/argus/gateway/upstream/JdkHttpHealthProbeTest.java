package dev.argus.gateway.upstream;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.argus.core.model.UpstreamInstance;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Adapter test: JdkHttpHealthProbe fits java.net.http to the HealthProbe port of the core
 * (FR-03). The stub backend is the JDK HttpServer.
 */
class JdkHttpHealthProbeTest {

    private final JdkHttpHealthProbe probe = new JdkHttpHealthProbe("/health", Duration.ofMillis(300));
    private HttpServer stub;

    @BeforeEach
    void startStub() throws IOException {
        stub = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        stub.start();
    }

    @AfterEach
    void stopStub() {
        stub.stop(0);
    }

    @Test
    @DisplayName("FR-03: an instance answering 200 on the health path is healthy")
    void okAnswerIsHealthy() {
        AtomicReference<String> seenPath = new AtomicReference<>();
        stub.createContext("/", exchange -> {
            seenPath.set(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        assertThat(probe.isHealthy(instanceAt(stub.getAddress().getPort()))).isTrue();
        assertThat(seenPath.get()).isEqualTo("GET /health");
    }

    @Test
    @DisplayName("FR-03: an instance answering 500 is not healthy")
    void errorAnswerIsUnhealthy() {
        stub.createContext("/", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        assertThat(probe.isHealthy(instanceAt(stub.getAddress().getPort()))).isFalse();
    }

    @Test
    @DisplayName("FR-03: an instance slower than the check timeout is not healthy")
    void slowAnswerIsUnhealthy() {
        stub.createContext("/", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        assertThat(probe.isHealthy(instanceAt(stub.getAddress().getPort()))).isFalse();
    }

    @Test
    @DisplayName("FR-03: an instance that cannot be reached is not healthy")
    void unreachableInstanceIsUnhealthy() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }

        assertThat(probe.isHealthy(instanceAt(closedPort))).isFalse();
    }

    private static UpstreamInstance instanceAt(int port) {
        return new UpstreamInstance(
                "stub-1", "orders", URI.create("http://127.0.0.1:" + port), 1, "v1");
    }
}
