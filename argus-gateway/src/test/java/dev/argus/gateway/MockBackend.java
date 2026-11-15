package dev.argus.gateway;

import dev.argus.mock.MockServiceApplication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * One real copy of the mock backend (upstreams/mock-service), started on a random port inside
 * the test JVM. Integration tests use it as the server side of the gateway.
 */
final class MockBackend implements AutoCloseable {

    private final String name;
    private final ConfigurableApplicationContext context;

    private MockBackend(String name, ConfigurableApplicationContext context) {
        this.name = name;
        this.context = context;
    }

    /** Starts a copy that calls itself {@code name} (its SERVICE_NAME), for example "orders-a". */
    static MockBackend start(String name) {
        // Passed as command-line arguments: they win over the gateway's application.yml, which
        // is on the same test classpath and would otherwise give every copy port 8080.
        ConfigurableApplicationContext context =
                new SpringApplicationBuilder(MockServiceApplication.class)
                        .run(
                                "--server.port=0",
                                "--mock.service-name=" + name,
                                "--mock.slow-delay=3s",
                                "--spring.application.name=" + name,
                                "--spring.jmx.enabled=false",
                                // The gateway's readiness group names a health check that
                                // only the gateway has.
                                "--management.endpoint.health.validate-group-membership=false");
        return new MockBackend(name, context);
    }

    String name() {
        return name;
    }

    String url() {
        int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        return "http://127.0.0.1:" + port;
    }

    /** Switches this copy to ok, slow or fail through its real POST /fault endpoint. */
    void fault(String mode) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url() + "/fault?mode=" + mode))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<Void> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("fault switch failed: " + response.statusCode());
        }
    }

    @Override
    public void close() {
        context.close();
    }
}
