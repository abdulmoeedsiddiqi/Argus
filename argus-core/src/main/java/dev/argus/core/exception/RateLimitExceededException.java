package dev.argus.core.exception;

/** Short-term rate limit exceeded (429). */
public class RateLimitExceededException extends ClientRejectionException {

    private static final long serialVersionUID = 1L;

    public RateLimitExceededException(String message) {
        super(429, message);
    }
}
