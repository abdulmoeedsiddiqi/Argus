package dev.argus.core.pipeline;

/** HTTP methods understood by the gateway, with their retry-safety classification. */
public enum HttpMethod {
    GET(true), HEAD(true), OPTIONS(true), PUT(true), DELETE(true), POST(false), PATCH(false);

    private final boolean idempotent;

    HttpMethod(boolean idempotent) {
        this.idempotent = idempotent;
    }

    /** Idempotent methods may be retried without duplicating side effects (RFC 9110 section 9.2.2). */
    public boolean isIdempotent() {
        return idempotent;
    }
}
