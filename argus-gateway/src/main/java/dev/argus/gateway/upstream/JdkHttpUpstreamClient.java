package dev.argus.gateway.upstream;

import dev.argus.core.exception.NoHealthyUpstreamException;
import dev.argus.core.exception.UpstreamTimeoutException;
import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.UpstreamInstance;
import dev.argus.core.model.UpstreamResponse;
import dev.argus.core.routing.UpstreamClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * Adapter: fits the JDK's {@code java.net.http.HttpClient} to the core's {@link UpstreamClient}
 * port, so the core never imports HTTP client code. Calls block, which is cheap here because
 * each request runs on its own virtual thread (ADR-0006).
 */
public final class JdkHttpUpstreamClient implements UpstreamClient {

    /**
     * Headers that describe one network hop, or that the JDK client sets itself and refuses to
     * accept from callers. They are not copied to the upstream request.
     */
    private static final Set<String> NOT_FORWARDED = Set.of(
            "connection", "content-length", "expect", "host", "upgrade",
            "keep-alive", "transfer-encoding", "te", "trailer",
            "proxy-authenticate", "proxy-authorization");

    private final HttpClient http;

    public JdkHttpUpstreamClient() {
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    @Override
    public UpstreamResponse send(IncomingRequest request, UpstreamInstance target, Duration timeout) {
        HttpRequest outgoing = toHttpRequest(request, target, timeout);
        long startedAt = System.nanoTime();
        try {
            HttpResponse<byte[]> response = http.send(outgoing, BodyHandlers.ofByteArray());
            Duration latency = Duration.ofNanos(System.nanoTime() - startedAt);
            return new UpstreamResponse(
                    response.statusCode(), lowerCaseNames(response.headers().map()),
                    response.body(), target.id(), latency);
        } catch (HttpTimeoutException timedOut) {
            throw new UpstreamTimeoutException(
                    "upstream " + target.id() + " did not answer within " + timeout, timedOut);
        } catch (IOException unreachable) {
            throw new NoHealthyUpstreamException(
                    "upstream " + target.id() + " could not be reached", unreachable);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new NoHealthyUpstreamException(
                    "interrupted while calling upstream " + target.id(), interrupted);
        }
    }

    private static HttpRequest toHttpRequest(
            IncomingRequest request, UpstreamInstance target, Duration timeout) {
        byte[] body = request.body();
        BodyPublisher publisher =
                body.length == 0 ? BodyPublishers.noBody() : BodyPublishers.ofByteArray(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseOf(target) + request.path()))
                .timeout(timeout)
                .method(request.method().name(), publisher);
        request.headers().forEach((name, values) -> {
            if (!NOT_FORWARDED.contains(name.toLowerCase(Locale.ROOT))) {
                values.forEach(value -> builder.header(name, value));
            }
        });
        return builder.build();
    }

    /** Base URI without a trailing slash, so "http://host:8081/" + "/orders" has one slash. */
    private static String baseOf(UpstreamInstance target) {
        String base = target.baseUri().toString();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static Map<String, List<String>> lowerCaseNames(Map<String, List<String>> headers) {
        Map<String, List<String>> result = new HashMap<>();
        headers.forEach((name, values) ->
                result.put(name.toLowerCase(Locale.ROOT), List.copyOf(values)));
        return result;
    }
}
