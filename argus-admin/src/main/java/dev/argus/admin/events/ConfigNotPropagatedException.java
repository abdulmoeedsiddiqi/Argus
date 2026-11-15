package dev.argus.admin.events;

/**
 * A change was saved in the database but could not be sent to the configuration topic, so the
 * gateways do not know about it yet (HTTP 503). It is repaired by publishing the current state
 * again: {@code POST /admin/config/republish}, which also happens when the Admin API starts.
 */
public class ConfigNotPropagatedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConfigNotPropagatedException(String message, Throwable cause) {
        super(message, cause);
    }
}
