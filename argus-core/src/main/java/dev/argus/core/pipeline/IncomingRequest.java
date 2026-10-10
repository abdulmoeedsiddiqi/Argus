package dev.argus.core.pipeline;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable view of an HTTP request as it arrived at the gateway.
 * Immutability lets the same instance be shared safely across virtual threads.
 */
public record IncomingRequest(
        String requestId,
        HttpMethod method,
        String path,
        Map<String, List<String>> headers,
        byte[] body,
        String clientIp,
        Instant receivedAt) {

    public IncomingRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(receivedAt, "receivedAt");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
