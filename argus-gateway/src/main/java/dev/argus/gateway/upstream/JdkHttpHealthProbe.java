package dev.argus.gateway.upstream;

import dev.argus.core.model.UpstreamInstance;
import dev.argus.core.routing.HealthProbe;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Objects;

/**
 * Adapter: implements the core's {@link HealthProbe} port with an HTTP GET on a health path.
 * An instance is healthy only if it answers with a 2xx status within the timeout; an error
 * status, a timeout or a refused connection all mean "not healthy".
 */
public final class JdkHttpHealthProbe implements HealthProbe {

    private final String path;
    private final Duration timeout;
    private final HttpClient http;

    public JdkHttpHealthProbe(String path, Duration timeout) {
        this.path = Objects.requireNonNull(path, "path");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .build();
    }

    @Override
    public boolean isHealthy(UpstreamInstance instance) {
        String base = instance.baseUri().toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(base + path))
                .timeout(timeout)
                .GET()
                .build();
        try {
            HttpResponse<Void> response = http.send(request, BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (IOException unreachableOrTimedOut) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
