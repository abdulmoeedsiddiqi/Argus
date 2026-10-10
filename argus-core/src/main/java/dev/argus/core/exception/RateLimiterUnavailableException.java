package dev.argus.core.exception;

/** The shared rate-limit store cannot be reached. */
public class RateLimiterUnavailableException extends InfrastructureUnavailableException {

    private static final long serialVersionUID = 1L;

    public RateLimiterUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
