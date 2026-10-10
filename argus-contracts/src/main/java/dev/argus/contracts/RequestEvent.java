package dev.argus.contracts;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Published by every gateway instance for every request (topic gateway.request-events,
 * keyed by apiKeyId). {@code eventId} makes processing idempotent: consumers ignore
 * an eventId they have already stored.
 */
public record RequestEvent(
        UUID eventId,
        int schemaVersion,
        String gatewayInstanceId,
        String requestId,
        String apiKeyId,
        String consumerId,
        String routeId,
        String method,
        int status,
        String rejectionReason,
        long latencyMillis,
        Instant occurredAt) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public RequestEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(gatewayInstanceId, "gatewayInstanceId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public boolean isAuthenticationFailure() {
        return "AUTHENTICATION_FAILED".equals(rejectionReason);
    }
}
