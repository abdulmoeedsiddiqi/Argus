package dev.argus.core.pipeline;

/** Machine-readable reason for a rejected request; also published in request events. */
public enum RejectionReason {
    AUTHENTICATION_FAILED,
    ACCESS_BLOCKED,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    ROUTE_NOT_FOUND,
    NO_HEALTHY_UPSTREAM,
    CIRCUIT_OPEN,
    UPSTREAM_TIMEOUT,
    INVALID_REQUEST
}
