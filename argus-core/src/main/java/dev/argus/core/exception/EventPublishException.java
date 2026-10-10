package dev.argus.core.exception;

/** A request event could not be handed to the event platform. */
public class EventPublishException extends InfrastructureUnavailableException {

    private static final long serialVersionUID = 1L;

    public EventPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
