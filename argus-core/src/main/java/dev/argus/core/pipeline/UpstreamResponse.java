package dev.argus.core.pipeline;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable response received from an upstream service. */
public record UpstreamResponse(
        int status,
        Map<String, List<String>> headers,
        byte[] body,
        String upstreamInstanceId,
        Duration latency) {

    public UpstreamResponse {
        Objects.requireNonNull(latency, "latency");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
