package dev.argus.core.exception;

/** Root of all gateway exceptions. Unchecked, so module boundaries stay clean. */
public abstract class ArgusException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    protected ArgusException(String message) {
        super(message);
    }

    protected ArgusException(String message, Throwable cause) {
        super(message, cause);
    }
}
