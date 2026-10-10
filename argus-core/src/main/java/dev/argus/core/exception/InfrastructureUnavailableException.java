package dev.argus.core.exception;

/**
 * A supporting system (Redis, the event platform) is unavailable. Callers handle this by
 * degrading gracefully (local fallback, spill to disk), never by failing the client request.
 */
public abstract class InfrastructureUnavailableException extends ArgusException {

    private static final long serialVersionUID = 1L;

    protected InfrastructureUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
