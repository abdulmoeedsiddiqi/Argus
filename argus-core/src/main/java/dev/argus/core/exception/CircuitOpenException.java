package dev.argus.core.exception;

/** Circuit breaker is open for the upstream service (503). */
public class CircuitOpenException extends UpstreamException {

    private static final long serialVersionUID = 1L;

    public CircuitOpenException(String message, Throwable cause) {
        super(503, message, cause);
    }
}
