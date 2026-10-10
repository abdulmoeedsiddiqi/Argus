package dev.argus.core.exception;

/** A route, plan or policy failed validation. Rejected before it can reach any gateway. */
public class InvalidConfigurationException extends ArgusException {

    private static final long serialVersionUID = 1L;

    public InvalidConfigurationException(String message) {
        super(message);
    }
}
